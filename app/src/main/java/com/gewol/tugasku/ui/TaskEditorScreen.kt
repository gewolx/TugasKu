package com.gewol.tugasku.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.UploadFile
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
import com.gewol.tugasku.TaskTemplate
import com.gewol.tugasku.addUnique
import android.widget.Toast
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    existing: Task?,
    use24Hour: Boolean,
    defaultReminders: List<Int>,
    subjectColors: Map<String, Int>,
    onSubjectColor: (String, Int) -> Unit,
    templates: List<TaskTemplate>,
    existingTags: List<String>,
    onSaveTemplate: (TaskTemplate) -> Unit,
    onSave: (Task) -> Unit,
    onBack: () -> Unit,
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
    var tags by rememberSaveable { mutableStateOf(existing?.tags ?: emptyList()) }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var members by rememberSaveable { mutableStateOf(existing?.members ?: emptyList()) }
    var memberInput by rememberSaveable { mutableStateOf("") }
    var splitNote by rememberSaveable { mutableStateOf(existing?.splitNote.orEmpty()) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var colorOverride by rememberSaveable { mutableIntStateOf(-1) }
    var attachments by rememberSaveable {
        mutableStateOf(existing?.attachments ?: emptyList())
    }
    var linkInput by rememberSaveable { mutableStateOf("") }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newPaths = uris.mapNotNull { uri -> importPhoto(context, uri) }
            attachments = attachments + newPaths
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newPaths = uris.mapNotNull { uri -> importFile(context, uri) }
            attachments = attachments + newPaths
        }
    }
    var showError by rememberSaveable { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showCustomReminderDialog by remember { mutableStateOf(false) }
    var editingCustomReminder by remember { mutableStateOf<Int?>(null) }

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

    val addTag: () -> Unit = {
        tags = tags.addUnique(tagInput.trim().trimStart('#'))
        tagInput = ""
    }
    val addMember: () -> Unit = {
        members = members.addUnique(memberInput)
        memberInput = ""
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
            if (existing == null && templates.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showTemplatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.template_use)) }
            }
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

            Text(stringResource(R.string.editor_tags_heading), style = MaterialTheme.typography.titleMedium)
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { tags = tags - tag },
                            label = { Text("#$tag") },
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier.size(InputChipDefaults.IconSize)
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text(stringResource(R.string.editor_tag_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag() }),
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                )
                Button(
                    onClick = addTag,
                    enabled = tagInput.isNotBlank(),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.add)) }
            }
            val suggestions = existingTags.filter { s -> tags.none { it.equals(s, ignoreCase = true) } }
            if (suggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { s ->
                        SuggestionChip(
                            onClick = { tags = tags.addUnique(s) },
                            label = { Text("#$s") },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }

            Text(stringResource(R.string.editor_group_heading), style = MaterialTheme.typography.titleMedium)
            if (members.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    members.forEach { name ->
                        InputChip(
                            selected = false,
                            onClick = { members = members - name },
                            label = { Text(name) },
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier.size(InputChipDefaults.IconSize)
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = memberInput,
                    onValueChange = { memberInput = it },
                    placeholder = { Text(stringResource(R.string.editor_member_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { addMember() }),
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                )
                Button(
                    onClick = addMember,
                    enabled = memberInput.isNotBlank(),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.add)) }
            }
            if (members.isNotEmpty() || memberInput.isNotBlank()) {
                OutlinedTextField(
                    value = splitNote,
                    onValueChange = { splitNote = it },
                    label = { Text(stringResource(R.string.editor_split_note)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.editor_add_file))
                }
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f),
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
            }

            val links = attachments.filter { isLinkAttachment(it) }
            if (links.isNotEmpty()) {
                links.forEach { link ->
                    LinkAttachmentCard(
                        link = link,
                        onOpen = { openLink(context, link) },
                        onDelete = { attachments = attachments - link }
                    )
                }
            }

            val documents = attachments.filter { !isLinkAttachment(it) && !isImageAttachment(it) }
            if (documents.isNotEmpty()) {
                documents.forEach { path ->
                    FileAttachmentCard(
                        path = path,
                        onOpen = { openFile(context, path) },
                        onDelete = {
                            attachments = attachments - path
                            if (existing?.attachments?.contains(path) != true) deleteAttachmentFile(path)
                        }
                    )
                }
            }

            val photos = attachments.filter { isImageAttachment(it) }
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
                                    if (existing?.attachments?.contains(path) != true) deleteAttachmentFile(path)
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

                val customReminders = remind.filter { m -> REMIND_OPTIONS.none { it.first == m } }
                items(customReminders) { minutes ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            editingCustomReminder = minutes
                            showCustomReminderDialog = true
                        },
                        label = { Text(reminderLabel(context, minutes)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.delete),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { remind = remind - minutes }
                            )
                        },
                        shape = RoundedCornerShape(50)
                    )
                }

                item {
                    FilterChip(
                        selected = false,
                        onClick = {
                            editingCustomReminder = null
                            showCustomReminderDialog = true
                        },
                        label = { Text(stringResource(R.string.remind_custom)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
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

            OutlinedButton(
                onClick = { showSaveTemplate = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) { Text(stringResource(R.string.template_save)) }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (title.isBlank()) {
                        showError = true
                    } else {
                        addSubtask()
                        addTag()
                        addMember()
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
                                subtasks = subtasks,
                                tags = tags.addUnique(tagInput.trim().trimStart('#')),
                                members = members.addUnique(memberInput),
                                splitNote = if (members.isEmpty() && memberInput.isBlank()) "" else splitNote.trim()
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

    if (showTemplatePicker) {
        AlertDialog(
            onDismissRequest = { showTemplatePicker = false },
            title = { Text(stringResource(R.string.template_pick_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { tpl ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (tpl.title.isNotBlank()) title = tpl.title
                                    subject = tpl.subject
                                    lecturer = tpl.lecturer
                                    note = tpl.note
                                    priority = tpl.priority
                                    remind = tpl.reminders
                                    tags = tpl.tags
                                    val base = System.currentTimeMillis()
                                    subtasks = tpl.subtasks.mapIndexed { i, t -> Subtask(base + i, t) }
                                    showError = false
                                    showTemplatePicker = false
                                }
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(tpl.name, style = MaterialTheme.typography.titleMedium)
                                if (tpl.subtasks.isNotEmpty()) {
                                    Text(
                                        context.resources.getQuantityString(
                                            R.plurals.template_subtasks, tpl.subtasks.size, tpl.subtasks.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTemplatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showSaveTemplate) {
        var templateName by rememberSaveable { mutableStateOf(title.trim()) }
        val savedMessage = stringResource(R.string.template_saved)
        AlertDialog(
            onDismissRequest = { showSaveTemplate = false },
            title = { Text(stringResource(R.string.template_save)) },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text(stringResource(R.string.template_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.medium
                )
            },
            confirmButton = {
                TextButton(
                    enabled = templateName.isNotBlank(),
                    onClick = {
                        onSaveTemplate(
                            TaskTemplate(
                                id = System.currentTimeMillis(),
                                name = templateName.trim(),
                                title = title.trim(),
                                subject = subject.trim(),
                                lecturer = lecturer.trim(),
                                note = note.trim(),
                                priority = priority,
                                reminders = remind.sorted(),
                                tags = tags.addUnique(tagInput.trim().trimStart('#')),
                                subtasks = (subtasks.map { it.title } + subtaskInput.trim()).filter { it.isNotBlank() }
                            )
                        )
                        Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                        showSaveTemplate = false
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplate = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

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

    if (showCustomReminderDialog) {
        CustomReminderDialog(
            deadline = deadline,
            use24Hour = use24Hour,
            initialMinutes = editingCustomReminder,
            onDismiss = {
                showCustomReminderDialog = false
                editingCustomReminder = null
            },
            onSave = { oldMinutes, newMinutes ->
                var list = remind
                if (oldMinutes != null) list = list - oldMinutes
                remind = (list + newMinutes).distinct().sorted()
                showCustomReminderDialog = false
                editingCustomReminder = null
            },
            onDelete = { minutesToDelete ->
                remind = remind - minutesToDelete
                showCustomReminderDialog = false
                editingCustomReminder = null
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomReminderDialog(
    deadline: Long,
    use24Hour: Boolean,
    initialMinutes: Int?,
    onDismiss: () -> Unit,
    onSave: (oldMinutes: Int?, newMinutes: Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val deadlineLocal = remember(deadline) {
        Instant.ofEpochMilli(deadline).atZone(zone).toLocalDateTime()
    }

    var selectedTab by rememberSaveable {
        mutableIntStateOf(
            if (initialMinutes != null && initialMinutes % 60 != 0 && initialMinutes > 60) 1 else 0
        )
    }

    val initialUnit = remember(initialMinutes) {
        when {
            initialMinutes == null -> 0
            initialMinutes % 10080 == 0 -> 3
            initialMinutes % 1440 == 0 -> 2
            initialMinutes % 60 == 0 -> 1
            else -> 0
        }
    }
    val initialAmount = remember(initialMinutes, initialUnit) {
        when {
            initialMinutes == null -> "30"
            initialUnit == 3 -> (initialMinutes / 10080).toString()
            initialUnit == 2 -> (initialMinutes / 1440).toString()
            initialUnit == 1 -> (initialMinutes / 60).toString()
            else -> initialMinutes.toString()
        }
    }
    var amountText by rememberSaveable { mutableStateOf(initialAmount) }
    var unitIndex by rememberSaveable { mutableIntStateOf(initialUnit) }

    val amount = amountText.toIntOrNull() ?: 0
    val multiplier = when (unitIndex) {
        0 -> 1
        1 -> 60
        2 -> 1440
        else -> 10080
    }
    val relativeMinutes = amount * multiplier
    val relativeTriggerMillis = deadline - relativeMinutes * 60_000L

    val initialDateTime = remember(initialMinutes) {
        if (initialMinutes != null) {
            Instant.ofEpochMilli(deadline - initialMinutes * 60_000L).atZone(zone).toLocalDateTime()
        } else {
            val now = LocalDateTime.now()
            if (deadlineLocal.isAfter(now.plusHours(1))) deadlineLocal.minusHours(1)
            else if (deadlineLocal.isAfter(now)) deadlineLocal.minusMinutes(30)
            else now.plusMinutes(15)
        }
    }
    var exactDate by rememberSaveable { mutableStateOf(initialDateTime.toLocalDate()) }
    var exactTime by rememberSaveable { mutableStateOf(initialDateTime.toLocalTime()) }
    var showExactDatePicker by remember { mutableStateOf(false) }
    var showExactTimePicker by remember { mutableStateOf(false) }

    val exactTriggerMillis = remember(exactDate, exactTime) {
        LocalDateTime.of(exactDate, exactTime).atZone(zone).toInstant().toEpochMilli()
    }
    val exactDiffMinutes = ((deadline - exactTriggerMillis) / 60_000L).toInt()

    val nowMillis = System.currentTimeMillis()

    val isValid = if (selectedTab == 0) {
        amount > 0
    } else {
        exactTriggerMillis <= deadline && exactDiffMinutes >= 0
    }

    val calculatedMinutes = if (selectedTab == 0) relativeMinutes else exactDiffMinutes
    val currentTriggerMillis = if (selectedTab == 0) relativeTriggerMillis else exactTriggerMillis
    val isPast = currentTriggerMillis <= nowMillis

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialMinutes != null) R.string.remind_custom_edit_title
                    else R.string.remind_custom_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text(stringResource(R.string.remind_custom_tab_relative)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text(stringResource(R.string.remind_custom_tab_exact)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    )
                }

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                amountText = input
                            }
                        },
                        label = { Text(stringResource(R.string.remind_custom_amount_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        isError = amount <= 0 && amountText.isNotEmpty()
                    )

                    val units = listOf(
                        R.string.remind_unit_minutes,
                        R.string.remind_unit_hours,
                        R.string.remind_unit_days,
                        R.string.remind_unit_weeks
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        units.forEachIndexed { index, resId ->
                            FilterChip(
                                selected = unitIndex == index,
                                onClick = { unitIndex = index },
                                label = { Text(stringResource(resId), style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PickerButton(
                            icon = Icons.Outlined.CalendarMonth,
                            text = formatDate(exactTriggerMillis),
                            modifier = Modifier.weight(1.3f),
                            onClick = { showExactDatePicker = true }
                        )
                        PickerButton(
                            icon = Icons.Outlined.Schedule,
                            text = formatTime(exactTriggerMillis, use24Hour),
                            modifier = Modifier.weight(1f),
                            onClick = { showExactTimePicker = true }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (selectedTab == 0 && amount <= 0) {
                        Text(
                            stringResource(R.string.remind_custom_err_positive),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (selectedTab == 1 && exactTriggerMillis > deadline) {
                        Text(
                            stringResource(R.string.remind_custom_err_after_deadline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            stringResource(
                                R.string.remind_custom_preview,
                                formatDeadline(currentTriggerMillis, use24Hour)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isPast) {
                            Text(
                                stringResource(R.string.remind_custom_warn_past),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onSave(initialMinutes, calculatedMinutes) }
            ) {
                Text(stringResource(if (initialMinutes != null) R.string.save else R.string.add))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (initialMinutes != null) {
                    TextButton(onClick = { onDelete(initialMinutes) }) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )

    if (showExactDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = exactDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showExactDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { picked ->
                        exactDate = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showExactDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showExactDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showExactTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = exactTime.hour,
            initialMinute = exactTime.minute,
            is24Hour = use24Hour
        )
        AlertDialog(
            onDismissRequest = { showExactTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    exactTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showExactTimePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showExactTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}
