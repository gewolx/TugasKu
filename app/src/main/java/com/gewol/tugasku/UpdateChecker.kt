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

/**
 * Cek pembaruan lewat file version.json di GitHub, unduh APK, lalu buka pemasang Android.
 *
 * Format version.json:
 * {"versionCode": 2, "versionName": "1.1", "apkUrl": "https://github.com/gewolx/TugasKu/releases/download/v1.1/TugasKu.apk", "notes": "..."}
 */
object UpdateChecker {

    private const val VERSION_URL = "https://raw.githubusercontent.com/gewolx/TugasKu/main/version.json"

    /** APK hanya diterima dari release repo ini. */
    private const val APK_URL_PREFIX = "https://github.com/gewolx/TugasKu/releases/download/"

    data class UpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val notes: String
    )

    fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"

    /** Mengembalikan info pembaruan bila ada versi lebih baru, null bila sudah terbaru. Melempar error bila gagal terhubung. */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        // Parameter t menghindari cache GitHub supaya versi terbaru langsung terbaca.
        val conn = URL("$VERSION_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.useCaches = false
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val code = json.getLong("versionCode")
            val apkUrl = json.optString("apkUrl")
            if (code <= installedVersionCode(context) || !apkUrl.startsWith(APK_URL_PREFIX)) {
                null
            } else {
                UpdateInfo(
                    versionCode = code,
                    versionName = json.optString("versionName").ifBlank { code.toString() },
                    apkUrl = apkUrl,
                    notes = json.optString("notes")
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Unduh APK ke cache aplikasi. [onProgress] menerima 0..100 (tidak dipanggil bila ukuran file tidak diketahui). */
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
                // Pastikan file benar-benar APK TugasKu sebelum dipasang.
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

    /** Android 8+ meminta izin "pasang aplikasi tidak dikenal" per aplikasi. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Buka layar pemasangan Android untuk APK yang sudah diunduh. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
