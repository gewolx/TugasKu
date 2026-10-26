package com.gewol.tugasku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gewol.tugasku.R
import com.gewol.tugasku.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(info: UpdateChecker.UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(-1) }
    var failed by remember { mutableStateOf(false) }
    var needPermission by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.update_message,
                        info.versionName,
                        UpdateChecker.installedVersionName(context)
                    )
                )
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                if (downloading) {
                    if (progress in 0..100) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        stringResource(R.string.update_downloading),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                if (needPermission) {
                    Text(
                        stringResource(R.string.update_permission_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.primary
                    )
                }
                if (failed) {
                    Text(
                        stringResource(R.string.update_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    if (!UpdateChecker.canInstall(context)) {
                        needPermission = true
                        UpdateChecker.openInstallPermissionSettings(context)
                    } else {
                        needPermission = false
                        failed = false
                        progress = -1
                        downloading = true
                        job = scope.launch {
                            val result = runCatching {
                                UpdateChecker.download(context, info) { progress = it }
                            }
                            downloading = false
                            result.onSuccess { file ->
                                UpdateChecker.install(context, file)
                                onDismiss()
                            }.onFailure { e ->
                                if (e is CancellationException) throw e
                                failed = true
                            }
                        }
                    }
                }
            ) { Text(stringResource(R.string.update_now)) }
        },
        dismissButton = {
            TextButton(onClick = {
                job?.cancel()
                onDismiss()
            }) {
                Text(stringResource(if (downloading) R.string.cancel else R.string.update_later))
            }
        }
    )
}
