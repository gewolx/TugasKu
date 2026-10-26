package com.gewol.tugasku.ui

import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gewol.tugasku.R
import com.gewol.tugasku.TRASH_DAYS
import com.gewol.tugasku.Task

private const val DAY_MS = 24 * 60 * 60_000L
private const val HOUR_MS = 60 * 60_000L

private fun agoText(context: Context, diffMs: Long): String {
    val days = (diffMs / DAY_MS).toInt()
    val hours = (diffMs / HOUR_MS).toInt()
    return when {
        days >= 1 -> context.resources.getQuantityString(R.plurals.ago_days, days, days)
        hours >= 1 -> context.resources.getQuantityString(R.plurals.ago_hours, hours, hours)
        else -> context.getString(R.string.ago_just_now)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    archived: List<Task>,
    trash: List<Task>,
    use24Hour: Boolean,
    archiveDays: Int,
    now: Long,
    onBack: () -> Unit,
    onUnarchive: (Long) -> Unit,
    onMoveToTrash: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onDeleteForever: (Long) -> Unit,
    onEmptyTrash: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmForever by remember { mutableStateOf<Task?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archive_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text(stringResource(R.string.archive_tab, archived.size)) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        },
                        shape = RoundedCornerShape(50)
                    )
                    FilterChip(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text(stringResource(R.string.trash_tab, trash.size)) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        },
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            item {
                Text(
                    if (tab == 0) {
                        if (archiveDays > 0) stringResource(R.string.archive_info, archiveDays)
                        else stringResource(R.string.archive_info_never)
                    } else {
                        stringResource(R.string.trash_info, TRASH_DAYS)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            if (tab == 0) {
                if (archived.isEmpty()) {
                    item { EmptyNote(Icons.Outlined.Inventory2, stringResource(R.string.archive_empty)) }
                } else {
                    items(archived, key = { it.id }) { task ->
                        ArchiveItem(
                            task = task,
                            deadlineText = formatDeadline(task.deadline, use24Hour),
                            status = if (task.doneAt > 0L) {
                                stringResource(R.string.archive_done_ago, agoText(context, now - task.doneAt))
                            } else {
                                stringResource(R.string.archive_done)
                            },
                            primaryLabel = stringResource(R.string.unarchive),
                            onPrimary = { onUnarchive(task.id) },
                            secondaryLabel = stringResource(R.string.discard),
                            onSecondary = { onMoveToTrash(task.id) }
                        )
                    }
                }
            } else {
                if (trash.isEmpty()) {
                    item { EmptyNote(Icons.Outlined.Delete, stringResource(R.string.trash_empty)) }
                } else {
                    item {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Text(stringResource(R.string.empty_trash), color = scheme.error)
                        }
                    }
                    items(trash, key = { it.id }) { task ->
                        val elapsed = now - task.deletedAt
                        val daysLeft = ((TRASH_DAYS * DAY_MS - elapsed + DAY_MS - 1) / DAY_MS).coerceAtLeast(0)
                        ArchiveItem(
                            task = task,
                            deadlineText = formatDeadline(task.deadline, use24Hour),
                            status = stringResource(R.string.trash_deleted_ago, agoText(context, elapsed)) + " • " +
                                if (daysLeft >= 1) {
                                    context.resources.getQuantityString(
                                        R.plurals.trash_days_left, daysLeft.toInt(), daysLeft.toInt()
                                    )
                                } else {
                                    stringResource(R.string.trash_deleted_today)
                                },
                            primaryLabel = stringResource(R.string.restore),
                            onPrimary = { onRestore(task.id) },
                            secondaryLabel = stringResource(R.string.delete_forever),
                            onSecondary = { confirmForever = task }
                        )
                    }
                }
            }
        }
    }

    confirmForever?.let { task ->
        AlertDialog(
            onDismissRequest = { confirmForever = null },
            title = { Text(stringResource(R.string.dialog_forever_title)) },
            text = { Text(stringResource(R.string.dialog_forever_text, task.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteForever(task.id)
                    confirmForever = null
                }) { Text(stringResource(R.string.delete), color = scheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmForever = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.dialog_empty_title)) },
            text = {
                Text(context.resources.getQuantityString(R.plurals.dialog_empty_text, trash.size, trash.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    onEmptyTrash()
                    confirmEmpty = false
                }) { Text(stringResource(R.string.empty_confirm), color = scheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun EmptyNote(icon: ImageVector, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArchiveItem(
    task: Task,
    deadlineText: String,
    status: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 6.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (task.subject.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                MetaRow(Icons.AutoMirrored.Outlined.MenuBook, task.subject, MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            MetaRow(Icons.Outlined.CalendarMonth, deadlineText, MaterialTheme.typography.labelMedium)
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSecondary) {
                    Text(secondaryLabel, color = scheme.error)
                }
                TextButton(onClick = onPrimary) { Text(primaryLabel) }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: ImageVector, text: String, style: TextStyle) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
