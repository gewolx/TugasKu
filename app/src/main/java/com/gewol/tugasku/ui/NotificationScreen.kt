package com.gewol.tugasku.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gewol.tugasku.NOTIF_DEADLINE
import com.gewol.tugasku.NOTIF_DIGEST
import com.gewol.tugasku.NOTIF_UPDATE
import com.gewol.tugasku.NotifRecord
import com.gewol.tugasku.R
import com.gewol.tugasku.Task

private const val NH_HOUR = 60 * 60_000L
private const val NH_DAY = 24 * NH_HOUR

private val NOTIF_FILTERS = listOf(
    R.string.nh_filter_all,
    R.string.nh_filter_deadline,
    R.string.nh_filter_pending,
    R.string.nh_filter_update
)

private fun notifAgo(context: Context, diffMs: Long): String {
    val days = (diffMs / NH_DAY).toInt()
    val hours = (diffMs / NH_HOUR).toInt()
    return when {
        days >= 1 -> context.resources.getQuantityString(R.plurals.ago_days, days, days)
        hours >= 1 -> context.resources.getQuantityString(R.plurals.ago_hours, hours, hours)
        else -> context.getString(R.string.ago_just_now)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    records: List<NotifRecord>,
    tasks: List<Task>,
    now: Long,
    onBack: () -> Unit,
    onOpen: (NotifRecord) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    val activeIds = tasks.filter { !it.done }.map { it.id }.toSet()
    val tasksById = tasks.associateBy { it.id }

    val shown = records.filter { r ->
        when (filter) {
            1 -> r.type == NOTIF_DEADLINE
            2 -> r.type == NOTIF_DEADLINE && r.taskId in activeIds
            3 -> r.type == NOTIF_UPDATE
            else -> true
        }
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nh_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (records.any { !it.read }) {
                        IconButton(onClick = onMarkAllRead) {
                            Icon(
                                Icons.Outlined.DoneAll,
                                contentDescription = stringResource(R.string.nh_mark_all_read)
                            )
                        }
                    }
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.nh_clear_all)
                            )
                        }
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(NOTIF_FILTERS.size) { i ->
                        FilterChip(
                            selected = filter == i,
                            onClick = { filter = i },
                            label = { Text(stringResource(NOTIF_FILTERS[i])) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                item {
                    NotifEmpty(
                        stringResource(if (records.isEmpty()) R.string.nh_empty else R.string.nh_empty_filter)
                    )
                }
            } else {
                items(shown, key = { it.id }) { record ->
                    val task = tasksById[record.taskId]
                    val status: String? = if (record.type == NOTIF_DEADLINE) {
                        stringResource(
                            when {
                                task == null -> R.string.nh_status_gone
                                task.done -> R.string.nh_status_done
                                else -> R.string.nh_status_pending
                            }
                        )
                    } else null
                    NotifItem(
                        record = record,
                        ago = notifAgo(context, (now - record.time).coerceAtLeast(0L)),
                        status = status,
                        statusIsPending = record.type == NOTIF_DEADLINE && task != null && !task.done,
                        onClick = { onOpen(record) },
                        onDelete = { onDelete(record.id) }
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.nh_clear_title)) },
            text = { Text(stringResource(R.string.nh_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    confirmClear = false
                }) { Text(stringResource(R.string.nh_clear_confirm), color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun NotifEmpty(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.NotificationsNone,
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
private fun NotifItem(
    record: NotifRecord,
    ago: String,
    status: String?,
    statusIsPending: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val icon: ImageVector = when (record.type) {
        NOTIF_UPDATE -> Icons.Outlined.SystemUpdate
        NOTIF_DIGEST -> Icons.Outlined.WbSunny
        else -> Icons.Outlined.Alarm
    }
    val borderColor = if (record.read) scheme.outline.copy(alpha = 0.35f) else scheme.primary.copy(alpha = 0.7f)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (record.read) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!record.read) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(scheme.primary)
                        )
                    }
                }
                if (record.message.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        record.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ago,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                    if (status != null) {
                        Text(
                            "  •  ",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant
                        )
                        Text(
                            status,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (statusIsPending) scheme.error else scheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.nh_remove),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
