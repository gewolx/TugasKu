package com.gewol.tugasku.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.gewol.tugasku.isLinkAttachment
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import com.gewol.tugasku.PRIORITY_OPTIONS
import com.gewol.tugasku.Subtask
import androidx.compose.ui.unit.dp
import com.gewol.tugasku.PRIORITY_MEDIUM
import com.gewol.tugasku.REMIND_OPTIONS
import com.gewol.tugasku.R
import androidx.compose.ui.res.stringResource
import com.gewol.tugasku.Task
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Menyimpan daftar subtugas saat layar diputar. Format satu butir: "id|selesai(0/1)|judul". */
private val SubtasksSaver = listSaver<List<Subtask>, String>(
    save = { list -> list.map { "${it.id}|${if (it.done) 1 else 0}|${it.title}" } },
    restore = { saved ->
        saved.mapNotNull { line ->
            val p = line.split("|", limit = 3)
            val id = p.getOrNull(0)?.toLongOrNull()
            if (p.size == 3 && id != null) Subtask(id, p[2], p[1] == "1") else null
        }
    }
)

private fun defaultDeadline(): Long =
    LocalDateTime.now().plusDays(1).withHour(23).withMinute(59).withSecond(0).withNano(0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    existing: Task?,
    use24Hour: Boolean,
    defaultReminders: List<Int>,
    subjectColors: Map<String, Int>,
    onSubjectColor: (String, Int) -> Unit,
    onSave: (Task) -> Unit,
    onBack: () -> Unit,
    /** Isian awal untuk tugas baru (mis. dari teks yang dibagikan); diabaikan saat mengedit tugas. */
    initialTitle: String = "",
    initialNote: String = ""
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(existing?.title ?: initialTitle) }
    var subject by rememberSaveable { mutableStateOf(existing?.subject.orEmpty()) }
    var lecturer by rememberSaveable { mutableStateOf(existing?.lecturer.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: initialNote) }
    var deadline by rememberSaveable { mutableLongStateOf(existing?.deadline ?: defaultDeadline()) }
    var remind by rememberSaveable {
        mutableStateOf(existing?.reminders ?: defaultReminders)
    }
    var repeat by rememberSaveable { mutableStateOf(existing?.repeatWeekly ?: false) }
    var priority by rememberSaveable { mutableIntStateOf(existing?.priority ?: PRIORITY_MEDIUM) }
    var subtasks by rememberSaveable(stateSaver = SubtasksSaver) {
        mutableStateOf(existing?.subtasks ?: emptyList())
    }
    var subtaskInput by rememberSaveable { mutableStateOf("") }
    // -1 = pengguna belum memilih warna sendiri, pakai warna otomatis dari nama mata kuliah.
    var colorOverride by rememberSaveable { mutableIntStateOf(-1) }
    var attachments by rememberSaveable {
        mutableStateOf(existing?.attachments ?: emptyList())
    }
    var linkInput by rememberSaveable { mutableStateOf("") }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val path = importPhoto(context, uri)
            if (path != null) attachments = attachments + path
        }
    }
    var showError by rememberSaveable { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()
    val local = Instant.ofEpochMilli(deadline).atZone(zone).toLocalDateTime()

    val addSubtask: () -> Unit = {
        val text = subtaskInput.trim()
        if (text.isNotEmpty()) {
            val nextId = maxOf(System.currentTimeMillis(), (subtasks.maxOfOrNull { it.id } ?: 0L) + 1)
            subtasks = subtasks + Subtask(nextId, text)
            subtaskInput = ""
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing == null) stringResource(R.string.new_task) else stringResource(R.string.edit_task),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; if (it.isNotBlank()) showError = false },
                label = { Text(stringResource(R.string.editor_title_label)) },
                isError = showError,
                supportingText = if (showError) ({ Text(stringResource(R.string.editor_title_error)) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text(stringResource(R.string.editor_subject)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            if (subject.isNotBlank()) {
                val shownColor = if (colorOverride >= 0) colorOverride else subjectColorIndex(subject, subjectColors)
                Text(stringResource(R.string.editor_subject_color), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(SUBJECT_PALETTE.size) { i ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(SUBJECT_PALETTE[i])
                                .clickable { colorOverride = i },
                            contentAlignment = Alignment.Center
                        ) {
                            if (i == shownColor) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.cd_color_option, i + 1),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = lecturer,
                onValueChange = { lecturer = it },
                label = { Text(stringResource(R.string.editor_lecturer)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.editor_note)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Text(stringResource(R.string.editor_deadline_heading), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerButton(
                    icon = Icons.Outlined.CalendarMonth,
                    text = formatDate(deadline),
                    modifier = Modifier.weight(1f),
                    onClick = { showDate = true }
                )
                PickerButton(
                    icon = Icons.Outlined.Schedule,
                    text = formatTime(deadline, use24Hour),
                    modifier = Modifier.weight(1f),
                    onClick = { showTime = true }
                )
            }

            Text(stringResource(R.string.editor_priority_heading), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRIORITY_OPTIONS.forEach { (value, labelRes) ->
                    FilterChip(
                        selected = priority == value,
                        onClick = { priority = value },
                        label = { Text(stringResource(labelRes)) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(priorityColor(value))
                            )
                        },
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            Text(stringResource(R.string.editor_subtasks_heading), style = MaterialTheme.typography.titleMedium)
            subtasks.forEach { st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = st.done,
                        onCheckedChange = { checked ->
                            subtasks = subtasks.map { if (it.id == st.id) it.copy(done = checked) else it }
                        }
                    )
                    Text(
                        st.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (st.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (st.done) TextDecoration.LineThrough else null
                    )
                    IconButton(onClick = { subtasks = subtasks.filterNot { it.id == st.id } }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = subtaskInput,
                    onValueChange = { subtaskInput = it },
                    placeholder = { Text(stringResource(R.string.editor_subtask_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { addSubtask() }),
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                )
                Button(
                    onClick = addSubtask,
                    enabled = subtaskInput.isNotBlank(),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.add)) }
            }

            Text(stringResource(R.string.attachment), style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    placeholder = { Text(stringResource(R.string.editor_link_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                )
                Button(
                    onClick = {
                        if (linkInput.isNotBlank()) {
                            val link = normalizeLink(linkInput)
                            if (link !in attachments) attachments = attachments + link
                            linkInput = ""
                        }
                    },
                    enabled = linkInput.isNotBlank(),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.add)) }
            }
            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.editor_add_photo))
            }

            attachments.filter { isLinkAttachment(it) }.forEach { link ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { openLink(context, link) }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                link,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { attachments = attachments - link }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val photos = attachments.filterNot { isLinkAttachment(it) }
            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(photos) { path ->
                        Box {
                            PhotoImage(
                                path = path,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { previewPhoto = path },
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = {
                                    attachments = attachments - path
                                    // Hapus file hanya jika foto ini baru ditambahkan di layar ini.
                                    if (existing?.attachments?.contains(path) != true) deletePhotoFile(path)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text(stringResource(R.string.editor_remind_heading), style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(REMIND_OPTIONS) { (minutes, labelRes) ->
                    FilterChip(
                        selected = minutes in remind,
                        onClick = {
                            remind = if (minutes in remind) remind - minutes else remind + minutes
                        },
                        label = { Text(stringResource(labelRes)) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_repeat_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.editor_repeat_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = repeat, onCheckedChange = { repeat = it })
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (title.isBlank()) {
                        showError = true
                    } else {
                        // Teks subtugas yang sudah diketik tapi belum ditekan "Tambah" ikut disimpan.
                        addSubtask()
                        if (subject.isNotBlank() && colorOverride >= 0) onSubjectColor(subject.trim(), colorOverride)
                        onSave(
                            Task(
                                id = existing?.id ?: System.currentTimeMillis(),
                                title = title.trim(),
                                note = note.trim(),
                                subject = subject.trim(),
                                lecturer = lecturer.trim(),
                                deadline = deadline,
                                reminders = remind.sorted(),
                                done = existing?.done ?: false,
                                repeatWeekly = repeat,
                                attachments = attachments,
                                doneAt = existing?.doneAt ?: 0L,
                                archived = existing?.archived ?: false,
                                priority = priority,
                                subtasks = subtasks
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    previewPhoto?.let { PhotoDialog(it, onDismiss = { previewPhoto = null }) }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        deadline = LocalDateTime.of(date, local.toLocalTime())
                            .atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(
            initialHour = local.hour,
            initialMinute = local.minute,
            is24Hour = use24Hour
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    deadline = LocalDateTime.of(local.toLocalDate(), LocalTime.of(state.hour, state.minute))
                        .atZone(zone).toInstant().toEpochMilli()
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) }
        )
    }
}

@Composable
private fun PickerButton(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}
