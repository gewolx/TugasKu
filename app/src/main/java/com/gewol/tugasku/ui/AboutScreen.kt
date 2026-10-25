package com.gewol.tugasku.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gewol.tugasku.R
import com.gewol.tugasku.UpdateChecker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    developerEmail: String = "gewol.dev@gmail.com",
    onCheckUpdate: () -> Unit = {},
    checkingUpdate: Boolean = false,
    use24Hour: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    }.getOrDefault("1.0")

    // Status pengecekan terakhir; dibaca ulang setiap kali sebuah pengecekan selesai.
    val status = remember(checkingUpdate) { UpdateChecker.status(context) }
    val emailOk = developerEmail.contains("@") && !developerEmail.contains("GANTI_")

    // Catatan perubahan versi terpasang: satu baris = satu butir (tanda "-", "•", "*" di awal dibuang).
    val notes = remember(status.whatsNew) {
        status.whatsNew.lines().map { it.trim().trimStart('-', '•', '*', ' ') }.filter { it.isNotEmpty() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        containerColor = scheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_tugasku_about),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppTitle(
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary
                )
                Spacer(Modifier.height(10.dp))

                // Tombol cek pembaruan (berubah jadi "Perbarui" bila versi baru sudah diketahui)
                FilledTonalButton(onClick = onCheckUpdate, enabled = !checkingUpdate) {
                    if (checkingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            when {
                                checkingUpdate -> R.string.update_checking
                                status.availableName != null -> R.string.update_now
                                else -> R.string.update_check
                            }
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (status.availableName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(scheme.primary)
                        )
                        Text(
                            text = stringResource(R.string.about_update_available, status.availableName),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = lastCheckedText(context, status.lastCheck, use24Hour),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
            }

            // Yang baru di versi ini (disembunyikan bila belum ada catatan)
            if (notes.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = scheme.primary
                            )
                            Text(
                                text = stringResource(R.string.about_whats_new, versionName),
                                modifier = Modifier.padding(start = 10.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        notes.forEach { line ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "•",
                                    modifier = Modifier.width(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.primary
                                )
                                Text(
                                    line,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Code,
                            contentDescription = null,
                            tint = scheme.primary
                        )
                        Text(
                            text = stringResource(R.string.about_developed_by),
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Gewol",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.about_role),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = null,
                            tint = scheme.primary
                        )
                        Text(
                            text = stringResource(R.string.about_contact),
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = developerEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Email laporan sudah berisi versi aplikasi, versi Android, dan tipe HP.
                        FilledTonalButton(
                            onClick = {
                                sendMail(
                                    context,
                                    developerEmail,
                                    context.getString(R.string.about_report_subject, versionName),
                                    reportBody(context, versionName)
                                )
                            },
                            enabled = emailOk
                        ) {
                            Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.about_report),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                sendMail(context, developerEmail, context.getString(R.string.about_email_subject), null)
                            },
                            enabled = emailOk
                        ) {
                            Icon(Icons.Outlined.AlternateEmail, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.about_send_email),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_copyright),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/** Buka aplikasi email dengan penerima, subjek, dan (opsional) isi yang sudah terisi. */
private fun sendMail(context: Context, email: String, subject: String, body: String?) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${Uri.encode(email)}")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        if (body != null) putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { context.startActivity(intent) }
}

/** Isi email laporan: ruang untuk menulis masalah, lalu info perangkat (dibiarkan bahasa Inggris untuk developer). */
private fun reportBody(context: Context, versionName: String): String {
    val code = runCatching { UpdateChecker.installedVersionCode(context) }.getOrDefault(0L)
    val lang = context.resources.configuration.locales[0].language
    return buildString {
        append(context.getString(R.string.about_report_hint)).append("\n\n\n\n")
        append("---\n")
        append("App: TugasKu $versionName ($code)\n")
        append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        append("Language: $lang")
    }
}

/** "Terakhir dicek hari ini, 12:48" / "kemarin, ..." / tanggal; atau "Belum pernah dicek". */
private fun lastCheckedText(context: Context, lastCheck: Long, use24Hour: Boolean): String {
    if (lastCheck <= 0L) return context.getString(R.string.about_last_never)
    val locale = context.resources.configuration.locales[0]
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(lastCheck).atZone(zone)
    val time = moment.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))
    val daysAgo = ChronoUnit.DAYS.between(moment.toLocalDate(), LocalDate.now(zone))
    return when {
        daysAgo <= 0L -> context.getString(R.string.about_last_today, time)
        daysAgo == 1L -> context.getString(R.string.about_last_yesterday, time)
        else -> context.getString(
            R.string.about_last_date,
            moment.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)),
            time
        )
    }
}
