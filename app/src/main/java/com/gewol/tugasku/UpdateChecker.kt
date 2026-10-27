package com.gewol.tugasku

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object UpdateChecker {

    private const val VERSION_URL = "https://raw.githubusercontent.com/gewolx/TugasKu/main/version.json"

    private const val APK_URL_PREFIX = "https://github.com/gewolx/TugasKu/releases/download/"

    data class UpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val notes: String,
        val sha256: String // NEW: expected checksum, lowercase hex, from version.json
    )

    data class UpdateStatus(
        val lastCheck: Long,
        val availableName: String?,
        val whatsNew: String
    )

    private const val STATUS_PREFS = "tugasku_update"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_AVAIL_CODE = "avail_code"
    private const val KEY_AVAIL_NAME = "avail_name"
    private const val KEY_NEW_CODE = "whatsnew_code"
    private const val KEY_NEW_NOTES = "whatsnew_notes"
    private const val KEY_NEW_NOTES_ID = "whatsnew_notes_id"
    private const val KEY_NEW_NOTES_EN = "whatsnew_notes_en"

    private fun isIndonesian(context: Context): Boolean =
        context.resources.configuration.locales[0].language.let { it == "in" || it == "id" }

    private fun pickNotes(indonesian: Boolean, general: String, id: String, en: String): String =
        (if (indonesian) id else en).ifBlank { general }

    fun status(context: Context): UpdateStatus {
        val p = context.applicationContext.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
        val installed = installedVersionCode(context)
        return UpdateStatus(
            lastCheck = p.getLong(KEY_LAST_CHECK, 0L),
            availableName = if (p.getLong(KEY_AVAIL_CODE, 0L) > installed) p.getString(KEY_AVAIL_NAME, null) else null,
            whatsNew = if (p.getLong(KEY_NEW_CODE, 0L) == installed) {
                pickNotes(
                    isIndonesian(context),
                    p.getString(KEY_NEW_NOTES, "").orEmpty(),
                    p.getString(KEY_NEW_NOTES_ID, "").orEmpty(),
                    p.getString(KEY_NEW_NOTES_EN, "").orEmpty()
                )
            } else ""
        )
    }

    private fun saveStatus(
        context: Context,
        code: Long,
        name: String,
        notes: String,
        notesId: String,
        notesEn: String,
        installed: Long,
        apkValid: Boolean
    ) {
        val e = context.applicationContext.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE).edit()
        e.putLong(KEY_LAST_CHECK, System.currentTimeMillis())
        if (code > installed && apkValid) {
            e.putLong(KEY_AVAIL_CODE, code).putString(KEY_AVAIL_NAME, name)
        } else {
            e.putLong(KEY_AVAIL_CODE, 0L).remove(KEY_AVAIL_NAME)
        }
        if (code == installed) {
            e.putLong(KEY_NEW_CODE, code)
                .putString(KEY_NEW_NOTES, notes)
                .putString(KEY_NEW_NOTES_ID, notesId)
                .putString(KEY_NEW_NOTES_EN, notesEn)
        }
        e.apply()
    }

    fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = URL("$VERSION_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.useCaches = false
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val code = json.getLong("versionCode")
            val apkUrl = json.optString("apkUrl")
            val sha256 = json.optString("sha256").lowercase() // NEW: required field in version.json
            val installed = installedVersionCode(context)
            val looksValid = apkUrl.startsWith(APK_URL_PREFIX) && sha256.length == 64
            saveStatus(
                context,
                code,
                json.optString("versionName").ifBlank { code.toString() },
                json.optString("notes"),
                json.optString("notes_id"),
                json.optString("notes_en"),
                installed,
                looksValid
            )
            if (code <= installed || !looksValid) {
                null
            } else {
                UpdateInfo(
                    versionCode = code,
                    versionName = json.optString("versionName").ifBlank { code.toString() },
                    apkUrl = apkUrl,
                    notes = pickNotes(
                        isIndonesian(context),
                        json.optString("notes"),
                        json.optString("notes_id"),
                        json.optString("notes_en")
                    ),
                    sha256 = sha256
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "update.apk")

            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                val digest = MessageDigest.getInstance("SHA-256") // NEW
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            ensureActive()
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read) // NEW: feed checksum as we go
                            done += read
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != last) {
                                    last = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }

                // NEW: verify checksum BEFORE trusting the file at all
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualHash.equals(info.sha256, ignoreCase = true)) {
                    error("Checksum APK tidak cocok (kemungkinan file rusak atau dimanipulasi)")
                }

                // Existing check: package name must match (kept as extra guard)
                val archive = context.packageManager.getPackageArchiveInfo(file.path, 0)
                if (archive?.packageName != context.packageName) error("APK tidak valid")

                file
            } catch (e: Exception) {
                file.delete()
                throw e
            } finally {
                conn.disconnect()
            }
        }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
