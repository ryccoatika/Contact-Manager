package com.ryccoatika.contactmanager.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RAIL_LETTERS = ('A'..'Z').toList() + '#'

/** Slim A–Z + # rail on the right edge; tap or drag to jump between sections. */
@Composable
fun AlphabetRail(
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    var railHeightPx by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf<Char?>(null) }

    fun letterAt(y: Float): Char? {
        if (railHeightPx <= 0) return null
        val index = (y / railHeightPx * RAIL_LETTERS.size).toInt()
        return RAIL_LETTERS[index.coerceIn(0, RAIL_LETTERS.lastIndex)]
    }
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .width(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                .onSizeChanged { railHeightPx = it.height }
                // One gesture owns both a tap and a drag, so a plain tap jumps too.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        letterAt(down.position.y)?.let {
                            active = it
                            onLetterSelected(it)
                        }
                        var change = down
                        while (change.pressed) {
                            val event = awaitPointerEvent()
                            change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                change.consume()
                                letterAt(change.position.y)?.let {
                                    if (it != active) onLetterSelected(it)
                                    active = it
                                }
                            }
                        }
                        active = null
                    }
                },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RAIL_LETTERS.forEach { letter ->
                val on = letter == active
                Text(
                    letter.toString(),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // Touch hint: a large centered bubble of the letter under the finger.
        active?.let { letter ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }
    }
}
