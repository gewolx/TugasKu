package com.gewol.tugasku.ui

import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gewol.tugasku.LocaleHelper
import com.gewol.tugasku.R
import com.gewol.tugasku.REMIND_OPTIONS
import com.gewol.tugasku.SNOOZE_OPTIONS
import com.gewol.tugasku.TaskTemplate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val ARCHIVE_OPTIONS = listOf(3, 7, 14, 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    scrollState: ScrollState = rememberScrollState(),
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    themeColor: Int,
    onThemeColor: (Int) -> Unit,
    use24Hour: Boolean,
    onUse24Hour: (Boolean) -> Unit,
    snoozeMinutes: Int,
    onSnoozeMinutes: (Int) -> Unit,
    defaultReminders: List<Int>,
    onDefaultReminders: (List<Int>) -> Unit,
    archiveDays: Int,
    onArchiveDays: (Int) -> Unit,
    weekStartsSunday: Boolean,
    onWeekStartsSunday: (Boolean) -> Unit,
    digestEnabled: Boolean,
    onDigestEnabled: (Boolean) -> Unit,
    digestMinutes: Int,
    onDigestMinutes: (Int) -> Unit,
    templates: List<TaskTemplate>,
    onDeleteTemplate: (Long) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var confirmRestore by remember { mutableStateOf(false) }
    var showDigestTime by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<TaskTemplate?>(null) }

    templateToDelete?.let { tpl ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text(stringResource(R.string.template_delete_title)) },
            text = { Text(stringResource(R.string.template_delete_text, tpl.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTemplate(tpl.id)
                    templateToDelete = null
                }) { Text(stringResource(R.string.delete), color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDigestTime) {
        val state = rememberTimePickerState(
            initialHour = digestMinutes / 60,
            initialMinute = digestMinutes % 60,
            is24Hour = use24Hour
        )
        AlertDialog(
            onDismissRequest = { showDigestTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onDigestMinutes(state.hour * 60 + state.minute)
                    showDigestTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDigestTime = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = { TimePicker(state = state) }
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.restore_dialog_title)) },
            text = { Text(stringResource(R.string.restore_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    onRestore()
                }) { Text(stringResource(R.string.restore_confirm), color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(Icons.Outlined.Language, stringResource(R.string.language)) {
                ChipRow(
                    options = listOf(
                        LocaleHelper.SYSTEM to stringResource(R.string.lang_system),
                        LocaleHelper.INDONESIAN to stringResource(R.string.lang_indonesian),
                        LocaleHelper.ENGLISH to stringResource(R.string.lang_english)
                    ),
                    selected = currentLanguage,
                    onSelect = { if (it != currentLanguage) onSelectLanguage(it) }
                )
            }

            SettingsSection(Icons.Outlined.Palette, stringResource(R.string.settings_appearance)) {
                FieldLabel(stringResource(R.string.settings_theme))
                ChipRow(
                    options = listOf(
                        0 to stringResource(R.string.theme_system),
                        1 to stringResource(R.string.theme_light),
                        2 to stringResource(R.string.theme_dark)
                    ),
                    selected = themeMode,
                    onSelect = onThemeMode
                )
                FieldLabel(stringResource(R.string.settings_theme_color))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PALETTE_DISPLAY_ORDER) { i ->
                        val palette = APP_PALETTES[i]
                        FilterChip(
                            selected = themeColor == i,
                            onClick = { onThemeColor(i) },
                            label = { Text(stringResource(palette.nameRes)) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(palette.swatch)
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
                FieldLabel(stringResource(R.string.settings_time_format))
                ChipRow(
                    options = listOf(
                        true to stringResource(R.string.time_24h),
                        false to stringResource(R.string.time_12h)
                    ),
                    selected = use24Hour,
                    onSelect = onUse24Hour
                )
            }

            SettingsSection(Icons.Outlined.Notifications, stringResource(R.string.settings_notifications)) {
                FieldLabel(stringResource(R.string.settings_snooze))
                ChipRow(
                    options = SNOOZE_OPTIONS.map { (minutes, labelRes) -> minutes to stringResource(labelRes) },
                    selected = snoozeMinutes,
                    onSelect = onSnoozeMinutes
                )
                FieldLabel(stringResource(R.string.settings_default_reminders))
                Text(
                    stringResource(R.string.settings_default_reminders_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(REMIND_OPTIONS) { (minutes, labelRes) ->
                        val selected = minutes in defaultReminders
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onDefaultReminders(
                                    if (selected) defaultReminders - minutes
                                    else (defaultReminders + minutes).sorted()
                                )
                            },
                            label = { Text(stringResource(labelRes)) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                    val customDefaults = defaultReminders.filter { m -> REMIND_OPTIONS.none { it.first == m } }
                    items(customDefaults) { minutes ->
                        FilterChip(
                            selected = true,
                            onClick = {
                                onDefaultReminders(defaultReminders - minutes)
                            },
                            label = { Text(reminderLabel(context, minutes)) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }

            SettingsSection(Icons.Outlined.Schedule, stringResource(R.string.digest_setting_title)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.digest_setting_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = digestEnabled, onCheckedChange = onDigestEnabled)
                }
                if (digestEnabled) {
                    val timeText = LocalTime.of(digestMinutes / 60, digestMinutes % 60)
                        .format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FieldLabel(stringResource(R.string.digest_setting_time))
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { showDigestTime = true }) { Text(timeText) }
                    }
                }
            }

            SettingsSection(Icons.Outlined.Inventory2, stringResource(R.string.settings_tasks)) {
                FieldLabel(stringResource(R.string.settings_archive_after))
                ChipRow(
                    options = ARCHIVE_OPTIONS.map { days ->
                        days to if (days == 0) {
                            stringResource(R.string.settings_never)
                        } else {
                            context.resources.getQuantityString(R.plurals.days_count, days, days)
                        }
                    },
                    selected = archiveDays,
                    onSelect = onArchiveDays
                )
            }

            SettingsSection(Icons.Outlined.CalendarMonth, stringResource(R.string.calendar)) {
                FieldLabel(stringResource(R.string.settings_week_start))
                ChipRow(
                    options = listOf(
                        false to stringResource(R.string.week_monday),
                        true to stringResource(R.string.week_sunday)
                    ),
                    selected = weekStartsSunday,
                    onSelect = onWeekStartsSunday
                )
            }

            SettingsSection(Icons.Outlined.ContentCopy, stringResource(R.string.settings_templates)) {
                if (templates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.template_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                } else {
                    templates.forEach { tpl ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tpl.name, style = MaterialTheme.typography.titleMedium)
                                if (tpl.subtasks.isNotEmpty()) {
                                    Text(
                                        context.resources.getQuantityString(
                                            R.plurals.template_subtasks, tpl.subtasks.size, tpl.subtasks.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { templateToDelete = tpl }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            SettingsSection(Icons.Outlined.Backup, stringResource(R.string.settings_data)) {
                Text(
                    text = stringResource(R.string.settings_backup_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBackup) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(onClick = { confirmRestore = true }) {
                        Text(stringResource(R.string.backup_import))
                    }
                }
            }

            SettingsSection(Icons.Outlined.Info, stringResource(R.string.settings_about)) {
                Text(
                    text = stringResource(R.string.settings_about_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                TextButton(onClick = onAbout) {
                    Text(stringResource(R.string.settings_about_open))
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                shape = RoundedCornerShape(50)
            )
        }
    }
}
