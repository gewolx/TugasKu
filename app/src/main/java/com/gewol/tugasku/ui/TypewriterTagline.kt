package com.gewol.tugasku.ui

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.gewol.tugasku.R
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val TYPE_DELAY_MS = 40L
private const val ERASE_DELAY_MS = 18L
private const val HOLD_MS = 2800L
private const val PAUSE_MS = 250L
private const val STATIC_HOLD_MS = 5000L
private const val MIN_SCALE = 0.78f

@Composable
fun TypewriterTagline(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val phrases = stringArrayResource(R.array.taglines).toList()
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    var shown by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(phrases.firstOrNull().orEmpty()) }

    LaunchedEffect(phrases) {
        if (phrases.isEmpty()) return@LaunchedEffect
        var index = Random.nextInt(phrases.size)
        while (true) {
            val phrase = phrases[index]
            full = phrase
            if (reduceMotion) {
                shown = phrase
                delay(STATIC_HOLD_MS)
            } else {
                for (n in 1..phrase.length) {
                    shown = phrase.substring(0, n)
                    delay(TYPE_DELAY_MS)
                }
                delay(HOLD_MS)
                for (n in phrase.length - 1 downTo 0) {
                    shown = phrase.substring(0, n)
                    delay(ERASE_DELAY_MS)
                }
                delay(PAUSE_MS)
            }
            index = (index + 1) % phrases.size
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    val cursorAlpha = if (reduceMotion) 0f else blink

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val lineHeight = remember(style) {
        with(density) { measurer.measure("Ag", style).size.height.toDp() }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(lineHeight)
            .clearAndSetSemantics { contentDescription = full },
        contentAlignment = Alignment.CenterStart
    ) {
        val availablePx = constraints.maxWidth
        val scale = remember(full, availablePx, style) {
            val needed = measurer.measure(full + "|", style, softWrap = false, maxLines = 1).size.width
            if (needed <= availablePx || needed == 0) 1f else (availablePx.toFloat() / needed).coerceIn(MIN_SCALE, 1f)
        }
        Text(
            text = buildAnnotatedString {
                append(shown)
                withStyle(SpanStyle(color = accent.copy(alpha = cursorAlpha))) { append("|") }
            },
            style = style.copy(fontSize = style.fontSize * scale),
            color = color,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
