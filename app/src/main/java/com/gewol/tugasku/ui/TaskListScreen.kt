package com.gewol.tugasku.ui

import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import com.gewol.tugasku.isLinkAttachment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gewol.tugasku.TRASH_DAYS
import com.gewol.tugasku.DoneUndo
import com.gewol.tugasku.R
import com.gewol.tugasku.PRIORITY_MEDIUM
import com.gewol.tugasku.Task
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

private val FILTERS = listOf(R.string.filter_active, R.string.filter_overdue, R.string.filter_done, R.string.filter_all)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    tasks: List<Task>,
    use24Hour: Boolean,
    now: Long,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    unreadNotifCount: Int,
    onOpenNotifications: () -> Unit,
    archiveCount: Int,
    onOpenArchive: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Task) -> Unit,
    onToggleDone: (Long) -> DoneUndo?,
    onUndoDone: (DoneUndo) -> Unit,
    onDelete: (Long) -> Unit,
    onRestore: (Task) -> Unit,
    subjectColors: Map<String, Int>,
    sortByPriority: Boolean,
    onSortByPriority: (Boolean) -> Unit
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var grouped by rememberSaveable { mutableStateOf(false) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<Task?>(null) }

    val active = tasks.filter { !it.done }
    val overdue = active.filter { it.deadline < now }
    val soon = active.filter { it.deadline >= now && it.deadline - now < 24 * 60 * 60_000L }

    val allTags = tasks.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    val activeTag = tagFilter?.takeIf { f -> allTags.any { it.equals(f, ignoreCase = true) } }

    val shown = when (filter) {
        0 -> active
        1 -> overdue
        2 -> tasks.filter { it.done }
        else -> tasks
    }.filter { t ->
        activeTag == null || t.tags.any { it.equals(activeTag, ignoreCase = true) }
    }.filter { t ->
        query.isBlank() || (listOf(t.title, t.subject, t.lecturer, t.note) + t.tags + t.members)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }.sortedWith(
        if (sortByPriority) {
            compareBy<Task> { it.done }.thenByDescending { it.priority }.thenBy { it.deadline }
        } else {
            compareBy<Task> { it.done }.thenBy { it.deadline }
        }
    )

    val context = LocalContext.current
    val trashMessage = stringResource(R.string.snackbar_trash)
    val doneMessage = stringResource(R.string.snackbar_done)
    val undoLabel = stringResource(R.string.undo)

    val deleteWithUndo: (Task) -> Unit = { task ->
        onDelete(task.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(
                message = trashMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long
            )
            if (r == SnackbarResult.ActionPerformed) onRestore(task)
        }
    }

    val completeWithUndo: (Task) -> Unit = { task ->
        val undo = onToggleDone(task.id)
        if (undo != null) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(
                    message = doneMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long
                )
                if (r == SnackbarResult.ActionPerformed) onUndoDone(undo)
            }
        }
    }

    val renderCard: @Composable (Task) -> Unit = { task ->
        SwipeableTask(
            task = task,
            onDone = { completeWithUndo(task) },
            onDeleteSwiped = { deleteWithUndo(task) }
        ) {
            TaskCard(
                task = task,
                now = now,
                use24Hour = use24Hour,
                onToggle = { completeWithUndo(task) },
                onEdit = { onEdit(task) },
                onShare = { shareTask(context, task, use24Hour) },
                onDelete = { toDelete = task },
                subjectColors = subjectColors
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_task), fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTitle(
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Row(modifier = Modifier.offset(x = 4.dp)) {
                            HeaderButton(
                                Icons.Outlined.Notifications,
                                stringResource(R.string.nh_title),
                                onOpenNotifications,
                                badgeCount = unreadNotifCount
                            )
                            HeaderButton(Icons.Outlined.BarChart, stringResource(R.string.stats_title), onOpenStats)
                            HeaderButton(Icons.Outlined.CalendarMonth, stringResource(R.string.calendar), onOpenCalendar)
                            HeaderButton(Icons.Outlined.Settings, stringResource(R.string.settings_title), onOpenSettings)
                        }
                    }
                    TypewriterTagline()
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.linearGradient(LocalHeroGradient.current)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            context.resources.getQuantityString(R.plurals.active_tasks_count, active.size, active.size),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatPill(Icons.Outlined.HourglassTop, stringResource(R.string.stat_urgent), soon.size, Modifier.weight(1f).fillMaxHeight())
                            StatPill(Icons.Outlined.WarningAmber, stringResource(R.string.stat_overdue), overdue.size, Modifier.weight(1f).fillMaxHeight())
                            StatPill(Icons.Outlined.CheckCircle, stringResource(R.string.stat_done), tasks.count { it.done }, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) ({
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear))
                        }
                    }) else null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = grouped,
                            onClick = { grouped = !grouped },
                            label = { Text(stringResource(R.string.chip_by_subject)) },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onSortByPriority(!sortByPriority) },
                            leadingIcon = {
                                Icon(
                                    if (sortByPriority) Icons.Outlined.Flag else Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            label = {
                                Text(
                                    stringResource(
                                        if (sortByPriority) R.string.sort_by_priority else R.string.sort_by_deadline
                                    )
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }
                    items(FILTERS.size) { i ->
                        FilterChip(
                            selected = filter == i,
                            onClick = { filter = i },
                            label = { Text(stringResource(FILTERS[i])) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                    item {
                        AssistChip(
                            onClick = onOpenArchive,
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Inventory2,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            label = {
                                Text(
                                    stringResource(R.string.chip_archive_trash) +
                                            if (archiveCount > 0) " ($archiveCount)" else ""
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }

            if (allTags.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allTags) { tag ->
                            FilterChip(
                                selected = activeTag.equals(tag, ignoreCase = true),
                                onClick = {
                                    tagFilter = if (activeTag.equals(tag, ignoreCase = true)) null else tag
                                },
                                label = { Text("#$tag") },
                                shape = RoundedCornerShape(50)
                            )
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                item { EmptyState(filter) }
            } else if (grouped) {
                val groups = shown
                    .groupBy { it.subject }
                    .toSortedMap(compareBy<String> { it.isEmpty() }.thenBy { it.lowercase() })
                groups.forEach { (subject, list) ->
                    item(key = "header_$subject") {
                        GroupHeader(
                            title = subject.ifEmpty { stringResource(R.string.no_subject) },
                            count = list.size,
                            color = if (subject.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else subjectColor(subject, subjectColors)
                        )
                    }
                    items(list, key = { it.id }) { task -> renderCard(task) }
                }
            } else {
                items(shown, key = { it.id }) { task -> renderCard(task) }
            }
        }
    }

    toDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.dialog_trash_title)) },
            text = { Text(stringResource(R.string.dialog_trash_text, task.title, TRASH_DAYS)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteWithUndo(task)
                    toDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun HeaderButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    Box(modifier = Modifier.size(44.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badgeCount > 9) "9+" else badgeCount.toString(),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTask(
    task: Task,
    onDone: () -> Unit,
    onDeleteSwiped: () -> Unit,
    content: @Composable () -> Unit
) {
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnDelete by rememberUpdatedState(onDeleteSwiped)

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentOnDone()
                SwipeToDismissBoxValue.EndToStart -> currentOnDelete()
                else -> {}
            }
            false
        }
    )

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val dir = state.dismissDirection
            val bg = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Teal
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(bg)
                    .padding(horizontal = 24.dp),
                contentAlignment =
                    if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart
                    else Alignment.CenterEnd
            ) {
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> SwipeHint(
                        icon = if (task.done) Icons.Outlined.Replay else Icons.Outlined.CheckCircle,
                        label = if (task.done) stringResource(R.string.swipe_reactivate) else stringResource(R.string.swipe_done)
                    )
                    SwipeToDismissBoxValue.EndToStart -> SwipeHint(
                        icon = Icons.Outlined.Delete,
                        label = stringResource(R.string.swipe_delete)
                    )
                    else -> {}
                }
            }
        }
    ) { content() }
}

@Composable
private fun SwipeHint(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GroupHeader(title: String, count: Int, color: Color) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            context.resources.getQuantityString(R.plurals.tasks_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatPill(icon: ImageVector, label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = 0.9f)
            )
            Text("$value", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun EmptyState(filter: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (filter == 1) Icons.Outlined.CheckCircle else Icons.AutoMirrored.Outlined.Assignment,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (filter) {
                1 -> stringResource(R.string.empty_overdue)
                2 -> stringResource(R.string.empty_done)
                else -> stringResource(R.string.empty_default)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    task: Task,
    now: Long,
    use24Hour: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    subjectColors: Map<String, Int>
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    val links = task.attachments.filter { isLinkAttachment(it) }
    val photos = task.attachments.filter { isImageAttachment(it) }
    val documents = task.attachments.filter { !isLinkAttachment(it) && !isImageAttachment(it) }
    val isOverdue = !task.done && task.deadline < now
    val isSoon = !task.done && !isOverdue && task.deadline - now < 24 * 60 * 60_000L
    val accent = when {
        task.done -> scheme.outline
        isOverdue -> scheme.error
        isSoon -> Amber
        else -> scheme.primary
    }

    Card(
        onClick = onEdit,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f))
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (task.done) accent else Color.Transparent)
                        .border(2.dp, accent, CircleShape)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.done) Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (task.done) scheme.onSurfaceVariant else scheme.onSurface,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val showPriority = !task.done && task.priority != PRIORITY_MEDIUM
                    if (task.subject.isNotBlank() || showPriority) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (task.subject.isNotBlank()) SubjectChip(task.subject, subjectColors)
                            if (showPriority) PriorityPill(task.priority)
                        }
                    }
                    if (task.tags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            task.tags.forEach { tag ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocalOffer,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = scheme.primary
                                    )
                                    Text(tag, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (task.members.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = scheme.onSurfaceVariant
                            )
                            Text(
                                task.members.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (task.splitNote.isNotBlank()) {
                            Text(
                                task.splitNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 20.dp)
                            )
                        }
                    }
                    if (task.lecturer.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = scheme.onSurfaceVariant
                            )
                            Text(
                                task.lecturer,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                    if (task.note.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            task.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (links.isNotEmpty() || documents.isNotEmpty() || photos.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        links.take(2).forEach { link ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.clickable { openLink(context, link) }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = scheme.primary
                                )
                                Text(
                                    getDisplayName(link),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (links.size > 2) {
                            Text(
                                context.resources.getQuantityString(R.plurals.more_links, links.size - 2, links.size - 2),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        documents.take(2).forEach { filePath ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.clickable { openFile(context, filePath) }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = scheme.primary
                                )
                                Text(
                                    getDisplayName(filePath),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (documents.size > 2) {
                            Text(
                                context.resources.getQuantityString(R.plurals.file_count, documents.size - 2, documents.size - 2),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        if (photos.isNotEmpty()) {
                            Text(
                                context.resources.getQuantityString(R.plurals.photo_count, photos.size, photos.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.primary,
                                modifier = Modifier.clickable { previewPhoto = photos.first() }
                            )
                        }
                    }

                    if (task.subtasks.isNotEmpty()) {
                        val doneCount = task.subtasks.count { it.done }
                        val allDone = doneCount == task.subtasks.size
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (allDone) Teal else scheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.subtask_progress, doneCount, task.subtasks.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { doneCount / task.subtasks.size.toFloat() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = if (allDone) Teal else scheme.primary,
                                trackColor = scheme.surfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            formatDeadline(task.deadline, use24Hour),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (task.repeatWeekly) {
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Outlined.Repeat,
                                contentDescription = stringResource(R.string.cd_repeat_weekly),
                                modifier = Modifier.size(15.dp),
                                tint = scheme.onSurfaceVariant
                            )
                        }
                        if (!task.done) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                relativeLabel(context, task.deadline, now),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                    }
                }

                Column {
                    IconButton(onClick = onShare) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.share),
                            tint = scheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    previewPhoto?.let { PhotoDialog(it, onDismiss = { previewPhoto = null }) }
}
