package com.ryccoatika.contactmanager.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/** Distance from the viewport edge within which drag-select auto-scrolls. */
private const val DRAG_SELECT_EDGE_PX = 140f

/**
 * Long-press + drag range selection (Google-Photos style). The row's own
 * long-press enters selection mode and selects the anchor; dragging without
 * lifting then extends the selection from the anchor to whatever row the finger
 * is over — moving back shrinks it — while pre-existing selections are kept.
 * Near the viewport's top/bottom edge the list auto-scrolls so the drag can
 * keep selecting beyond the screen.
 */
internal fun Modifier.dragToSelect(
    listState: LazyListState,
    orderedIds: List<Long>,
    // Called at long-press with the anchor contact: toggles it (haptic included)
    // and returns the selection as it was BEFORE the press — the drag range then
    // toggles every row it covers against that baseline.
    onSelectAnchor: (Long) -> Set<Long>,
    setSelection: (Set<Long>) -> Unit,
    scrollBy: (Float) -> Unit,
): Modifier = pointerInput(orderedIds) {
    var anchorId: Long? = null
    var base: Set<Long> = emptySet()
    detectDragGesturesAfterLongPress(
        onDragStart = { position ->
            anchorId = listState.contactIdAt(position.y)
            base = anchorId?.let(onSelectAnchor) ?: emptySet()
        },
        onDragEnd = { anchorId = null },
        onDragCancel = { anchorId = null },
        onDrag = { change, _ ->
            val anchor = anchorId ?: return@detectDragGesturesAfterLongPress
            val y = change.position.y
            // Auto-scroll when dragging near the edges.
            val edge = DRAG_SELECT_EDGE_PX
            when {
                y < edge -> scrollBy(y - edge)
                y > size.height - edge -> scrollBy(y - (size.height - edge))
            }
            val overId = listState.contactIdAt(y) ?: return@detectDragGesturesAfterLongPress
            val anchorIndex = orderedIds.indexOf(anchor)
            val overIndex = orderedIds.indexOf(overId)
            if (anchorIndex == -1 || overIndex == -1) return@detectDragGesturesAfterLongPress
            val range = if (anchorIndex <= overIndex) {
                orderedIds.subList(anchorIndex, overIndex + 1)
            } else {
                orderedIds.subList(overIndex, anchorIndex + 1)
            }.toSet()
            // Toggle the covered rows against the pre-press baseline: rows that were
            // selected turn off, unselected ones turn on; dragging back reverts.
            setSelection((base - range) + (range - base))
        },
    )
}

/**
 * Long-press detector that coexists with a component's own click handling (e.g.
 * FilterChip): it watches the Initial pass, and only when the press outlasts the
 * long-press timeout does it fire and swallow the rest of the gesture so the
 * component's tap doesn't also land.
 */
internal fun Modifier.chipLongPress(key: Any?, onLongPress: () -> Unit): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // null = timed out (long press); true = lifted (tap); false = cancelled
            // (e.g. the chips row started scrolling).
            val upBeforeTimeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation(PointerEventPass.Initial) != null
            }
            if (upBeforeTimeout == null) {
                onLongPress()
                var event: PointerEvent
                do {
                    event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }

/** The contact row (Long key) under viewport-relative [y]; headers yield null. */
private fun LazyListState.contactIdAt(y: Float): Long? =
    layoutInfo.visibleItemsInfo
        .firstOrNull { y.toInt() in it.offset..(it.offset + it.size) }
        ?.key as? Long
