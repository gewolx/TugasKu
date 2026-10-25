package com.gewol.tugasku.ui

import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gewol.tugasku.R
import com.gewol.tugasku.Task
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ambang "minggu padat": jumlah tugas aktif dalam satu baris minggu. */
private const val BUSY_WEEK = 3
private const val VERY_BUSY_WEEK = 5

/** Jumlah maksimal titik berwarna di satu sel tanggal. */
private const val MAX_DOTS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    tasks: List<Task>,
    use24Hour: Boolean,
    weekStartsSunday: Boolean,
    now: Long,
    subjectColors: Map<String, Int>,
    onBack: () -> Unit,
    onEdit: (Task) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val context = LocalContext.current
    // Nama hari & bulan mengikuti bahasa aplikasi.
    val locale = Locale.getDefault()
    val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
    val weekDays = DayOfWeek.values().toList() // Senin ... Minggu
    val orderedDays = if (weekStartsSunday) listOf(DayOfWeek.SUNDAY) + weekDays.dropLast(1) else weekDays
    val dayNames = orderedDays.map { it.getDisplayName(TextStyle.SHORT, locale) }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    var monthIndex by rememberSaveable { mutableIntStateOf(today.year * 12 + today.monthValue - 1) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }

    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    val selected = LocalDate.ofEpochDay(selectedDay)

    val byDay = remember(tasks) {
        tasks.groupBy { Instant.ofEpochMilli(it.deadline).atZone(zone).toLocalDate() }
    }

    // Kolom pertama bulan: Senin = 1 -> 0 (atau Minggu = 7 -> 0 jika minggu dimulai hari Minggu)
    val firstDay = month.atDay(1).dayOfWeek.value
    val offset = if (weekStartsSunday) firstDay % 7 else firstDay - 1
    val weeks = (offset + month.lengthOfMonth() + 6) / 7
    val monthActive = byDay
        .filterKeys { YearMonth.from(it) == month }
        .values.sumOf { list -> list.count { !it.done } }

    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar), style = MaterialTheme.typography.titleLarge) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Navigasi bulan
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthIndex -= 1 }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.cal_prev_month))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        month.format(monthFormat).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        context.resources.getQuantityString(R.plurals.cal_month_active, monthActive, monthActive),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { monthIndex += 1 }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.cal_next_month))
                }
            }

            if (month != YearMonth.from(today)) {
                TextButton(
                    onClick = {
                        monthIndex = today.year * 12 + today.monthValue - 1
                        selectedDay = today.toEpochDay()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(stringResource(R.string.cal_this_month)) }
            }

            // Nama hari
            Row(Modifier.fillMaxWidth()) {
                dayNames.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            // Grid bulan, satu baris per minggu
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (w in 0 until weeks) {
                    val days = List(7) { c ->
                        val d = w * 7 + c - offset + 1
                        if (d in 1..month.lengthOfMonth()) month.atDay(d) else null
                    }
                    val weekActive = days.sumOf { d ->
                        d?.let { byDay[it]?.count { t -> !t.done } } ?: 0
                    }
                    val tint = when {
                        weekActive >= VERY_BUSY_WEEK -> scheme.error.copy(alpha = 0.16f)
                        weekActive >= BUSY_WEEK -> Amber.copy(alpha = 0.18f)
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(tint)
                            .padding(vertical = 2.dp)
                    ) {
                        days.forEach { date ->
                            if (date == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                DayCell(
                                    date = date,
                                    dayTasks = byDay[date].orEmpty(),
                                    isToday = date == today,
                                    isSelected = date == selected,
                                    now = now,
                                    subjectColors = subjectColors,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedDay = date.toEpochDay() }
                                )
                            }
                        }
                    }
                }
            }

            // Legenda
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(Amber.copy(alpha = 0.6f), stringResource(R.string.cal_legend_busy, BUSY_WEEK))
                LegendDot(scheme.error.copy(alpha = 0.6f), stringResource(R.string.cal_legend_very_busy, VERY_BUSY_WEEK))
            }

            HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))

            // Tugas pada tanggal terpilih
            Text(
                selected.format(dayFormat).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium
            )
            val dayTasks = byDay[selected].orEmpty().sortedWith(compareBy<Task> { it.done }.thenBy { it.deadline })
            if (dayTasks.isEmpty()) {
                Text(
                    stringResource(R.string.cal_no_deadlines),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            } else {
                dayTasks.forEach { task ->
                    Card(
                        onClick = { onEdit(task) },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = scheme.surface),
                        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (task.done) scheme.onSurfaceVariant else scheme.onSurface,
                                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (task.subject.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = subjectColor(task.subject, subjectColors)
                                        )
                                        Text(
                                            task.subject,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            val timeColor = if (!task.done && task.deadline < now) scheme.error else scheme.primary
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = timeColor
                                )
                                Text(
                                    formatTime(task.deadline, use24Hour),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = timeColor
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    dayTasks: List<Task>,
    isToday: Boolean,
    isSelected: Boolean,
    now: Long,
    subjectColors: Map<String, Int>,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val active = dayTasks.filter { !it.done }.sortedBy { it.deadline }
    val hasOverdue = active.any { it.deadline < now }

    Column(
        modifier = modifier
            .padding(2.dp)
            .height(54.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) scheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isToday) scheme.primary else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday || isSelected || hasOverdue) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isToday -> scheme.onPrimary
                    hasOverdue -> scheme.error
                    else -> scheme.onSurface
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        when {
            // Satu titik per tugas aktif (maksimal 4), warnanya mengikuti mata kuliah.
            active.isNotEmpty() -> Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                active.take(MAX_DOTS).forEach { task ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (task.subject.isBlank()) scheme.onSurfaceVariant
                                else subjectColor(task.subject, subjectColors)
                            )
                    )
                }
            }
            dayTasks.isNotEmpty() -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = scheme.onSurfaceVariant
            )
            else -> Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
