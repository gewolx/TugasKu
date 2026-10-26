package com.gewol.tugasku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gewol.tugasku.PRIORITY_HIGH
import com.gewol.tugasku.PRIORITY_LOW
import com.gewol.tugasku.priorityLabelRes
import com.gewol.tugasku.subjectKey

val SUBJECT_PALETTE = listOf(
    Color(0xFF5B5BD6),
    Color(0xFF14B8A6),
    Color(0xFFF59E0B),
    Color(0xFFEF4444),
    Color(0xFFEC4899),
    Color(0xFF8B5CF6),
    Color(0xFF3B82F6),
    Color(0xFF22C55E),
    Color(0xFFF97316),
    Color(0xFF64748B)
)

fun subjectColorIndex(subject: String, overrides: Map<String, Int>): Int {
    val key = subjectKey(subject)
    val chosen = overrides[key]
    if (chosen != null && chosen in SUBJECT_PALETTE.indices) return chosen
    return (key.hashCode() and Int.MAX_VALUE) % SUBJECT_PALETTE.size
}

fun subjectColor(subject: String, overrides: Map<String, Int>): Color =
    SUBJECT_PALETTE[subjectColorIndex(subject, overrides)]

@Composable
fun priorityColor(priority: Int): Color = when (priority) {
    PRIORITY_HIGH -> MaterialTheme.colorScheme.error
    PRIORITY_LOW -> Teal
    else -> Amber
}

@Composable
fun AppTitle(style: TextStyle, modifier: Modifier = Modifier, fontWeight: FontWeight? = null) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = buildAnnotatedString {
            append("Tugas")
            withStyle(SpanStyle(color = accent)) { append("Ku") }
        },
        modifier = modifier,
        style = style,
        fontWeight = fontWeight
    )
}

@Composable
fun SubjectChip(subject: String, overrides: Map<String, Int>, modifier: Modifier = Modifier) {
    val color = subjectColor(subject, overrides)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                subject,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PriorityPill(priority: Int, modifier: Modifier = Modifier) {
    val color = priorityColor(priority)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = color
            )
            Text(
                stringResource(priorityLabelRes(priority)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
