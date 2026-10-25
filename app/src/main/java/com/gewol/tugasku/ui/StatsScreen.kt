package com.gewol.tugasku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gewol.tugasku.R
import com.gewol.tugasku.Task
import com.gewol.tugasku.subjectKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

private const val WEEKS = 8
private const val MAX_SUBJECTS = 8
private val BAR_AREA = 132.dp
private val BAR_MAX = 100.dp

private class SubjectStat(val name: String, val total: Int, val done: Int)

private class Stats(

    val completed: Int,
    val onTime: Int,

    val unknownDone: Int,
    val overdueNow: Int,
    val weekStarts: List<LocalDate>,
    val weekCounts: List<Int>,
    val subjects: List<SubjectStat>
)

private fun computeStats(tasks: List<Task>, weekStartsSunday: Boolean, now: Long, zone: ZoneId): Stats {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val firstDay = if (weekStartsSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(firstDay))

    val thisWeek = weekStart(today)
    val starts = (WEEKS - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }

    val done = tasks.filter { it.done }
    val known = done.filter { it.doneAt > 0L }
    val counts = starts.map { start ->
        known.count { weekStart(Instant.ofEpochMilli(it.doneAt).atZone(zone).toLocalDate()) == start }
    }

    val subjects = tasks
        .groupBy { subjectKey(it.subject) }
        .values
        .map { list -> SubjectStat(list.first().subject.trim(), list.size, list.count { it.done }) }
        .sortedWith(compareByDescending<SubjectStat> { it.total }.thenBy { it.name.lowercase() })
        .take(MAX_SUBJECTS)

    return Stats(
        completed = known.size,
        onTime = known.count { it.doneAt <= it.deadline },
        unknownDone = done.size - known.size,
        overdueNow = tasks.count { !it.done && it.deadline < now },
        weekStarts = starts,
        weekCounts = counts,
        subjects = subjects
    )
}

/**
 * Statistik: tugas selesai per minggu, ketepatan waktu, dan jumlah tugas per mata kuliah.
 * [tasks] = tugas aktif + arsip (tanpa sampah). Tugas "tepat waktu" jika selesai sebelum atau tepat di tenggat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    tasks: List<Task>,
    now: Long,
    weekStartsSunday: Boolean,
    subjectColors: Map<String, Int>,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val weekLabel = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    val stats = remember(tasks, weekStartsSunday, now) { computeStats(tasks, weekStartsSunday, now, zone) }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleLarge) },
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (tasks.isEmpty()) {
                Text(
                    stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
                return@Column
            }

            // Ringkasan
            val percent = if (stats.completed > 0) (stats.onTime * 100f / stats.completed).roundToInt() else null
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stats.completed.toString(), stringResource(R.string.stat_done), scheme.primary, Modifier.weight(1f))
                StatTile(
                    percent?.let { "$it%" } ?: "-",
                    stringResource(R.string.stats_on_time),
                    Teal,
                    Modifier.weight(1f)
                )
                StatTile(
                    stats.overdueNow.toString(),
                    stringResource(R.string.stat_overdue),
                    if (stats.overdueNow > 0) scheme.error else scheme.onSurfaceVariant,
                    Modifier.weight(1f)
                )
            }

            // Selesai per minggu
            StatsCard(
                title = stringResource(R.string.stats_weekly_title),
                subtitle = stringResource(R.string.stats_this_week, stats.weekCounts.last())
            ) {
                val max = stats.weekCounts.maxOrNull() ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stats.weekStarts.forEachIndexed { i, start ->
                        val count = stats.weekCounts[i]
                        val isCurrent = i == stats.weekStarts.lastIndex
                        val label = start.format(weekLabel)
                        val barHeight: Dp = if (max == 0 || count == 0) 2.dp else BAR_MAX * (count.toFloat() / max)
                        val barColor = if (isCurrent) scheme.primary else scheme.primary.copy(alpha = 0.45f)
                        val description = stringResource(R.string.stats_week_cd, label, count)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .semantics(mergeDescendants = true) { contentDescription = description },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(BAR_AREA),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (count > 0) {
                                        Text(
                                            "$count",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = scheme.onSurface
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    Box(
                                        Modifier
                                            .width(22.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) scheme.primary else scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }

            // Ketepatan waktu
            StatsCard(title = stringResource(R.string.stats_ontime_title)) {
                if (percent == null) {
                    Text(
                        stringResource(R.string.stats_ontime_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                } else {
                    val rateColor = when {
                        percent >= 70 -> Teal
                        percent >= 40 -> Amber
                        else -> scheme.error
                    }
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = rateColor
                    )
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = rateColor,
                        trackColor = scheme.surfaceVariant
                    )
                    Text(
                        stringResource(R.string.stats_ontime_detail, stats.onTime, stats.completed),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                if (stats.unknownDone > 0) {
                    Text(
                        context.resources.getQuantityString(R.plurals.stats_old_data, stats.unknownDone, stats.unknownDone),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            // Tugas per mata kuliah
            StatsCard(
                title = stringResource(R.string.stats_subject_title),
                subtitle = stringResource(R.string.stats_subject_hint)
            ) {
                val maxTotal = stats.subjects.maxOf { it.total }
                stats.subjects.forEach { s ->
                    val color = if (s.name.isBlank()) scheme.onSurfaceVariant else subjectColor(s.name, subjectColors)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                s.name.ifBlank { stringResource(R.string.no_subject) },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(R.string.stats_subject_count, s.done, s.total),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(scheme.surfaceVariant)
                        ) {
                            // Panjang total = jumlah tugas, bagian pekat = yang sudah selesai.
                            Box(
                                Modifier
                                    .fillMaxWidth(s.total.toFloat() / maxTotal)
                                    .fillMaxHeight()
                                    .background(color.copy(alpha = 0.35f))
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(s.done.toFloat() / maxTotal)
                                    .fillMaxHeight()
                                    .background(color)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.primary)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}
