package com.ryccoatika.contactmanager.ui.home

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.ContactAvatar
import com.ryccoatika.contactmanager.ui.common.SelectedAvatar
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import com.ryccoatika.contactmanager.ui.theme.TabularNums
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Width of the revealed swipe-to-delete action button. */
private val DELETE_ACTION_WIDTH = 88.dp

/** Fraction of the row width past which releasing confirms deletion directly. */
private const val DELETE_SWIPE_THRESHOLD = 0.4f

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ContactRow(
    contact: Contact,
    extras: ContactRowExtras?,
    selected: Boolean,
    swipeEnabled: Boolean,
    closeReveal: Boolean = false,
    onSwipeStart: () -> Unit = {},
    onClick: () -> Unit,
    onSwipeDelete: () -> Unit,
    sharedElementEnabled: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Horizontal drag offset of the row: 0 = settled, -actionWidth = delete
    // button revealed. Driven manually so a partial swipe can *hold* the reveal
    // (SwipeToDismissBox only knows settled/dismissed). A plain state written
    // synchronously from the drag callback — launching per-delta coroutines
    // raced the release animation and left the row ajar.
    var offsetPx by remember { mutableFloatStateOf(0f) }
    val settleBack: () -> Unit = {
        scope.launch { animate(offsetPx, 0f) { value, _ -> offsetPx = value } }
    }
    // List scrolling dismisses an open reveal — a stale delete button shouldn't
    // ride along while the user browses.
    LaunchedEffect(closeReveal) {
        if (closeReveal && offsetPx < 0f) {
            animate(offsetPx, 0f) { value, _ -> offsetPx = value }
        }
    }
    val rowClick: () -> Unit = {
        // A tap while the delete button is revealed closes it instead of opening.
        if (offsetPx < -1f) settleBack() else onClick()
    }
    val row = @Composable {
        ListItem(
            // Long-press is handled by the list-level dragToSelect detector (a
            // long-press here would consume the events and kill drag-select).
            modifier = Modifier
                .heightIn(min = 64.dp)
                .clickable(onClick = rowClick),
            // Opaque so it covers the red delete background until swiped.
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            headlineContent = {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
            },
            supportingContent = {
                extras?.subtitle?.let {
                    Text(
                        it,
                        style = TabularNums.merge(MaterialTheme.typography.bodyMedium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
            leadingContent = {
                if (selected) {
                    SelectedAvatar()
                } else {
                    // Only the last-tapped row carries the shared element (set before
                    // navigating), so scrolling pays zero match bookkeeping per row
                    // while the open/back morphs still find their source.
                    val avatarModifier =
                        if (sharedElementEnabled &&
                            sharedTransitionScope != null && animatedVisibilityScope != null
                        ) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    rememberSharedContentState(key = "contact-avatar-${contact.contactId}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            }
                        } else {
                            Modifier
                        }
                    ContactAvatar(contact.displayName, contact.photoThumbnailUri, modifier = avatarModifier)
                }
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    extras?.accountDots?.forEach { (type, name) -> AccountDot(type, name) }
                }
            },
        )
    }

    if (!swipeEnabled) {
        Box(modifier) { row() }
        return
    }

    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val actionWidthPx = with(LocalDensity.current) { DELETE_ACTION_WIDTH.toPx() }
    // Crossing this while dragging buzzes; releasing past it confirms right away.
    val deleteThresholdPx = rowWidthPx * DELETE_SWIPE_THRESHOLD
    var pastThreshold by remember { mutableStateOf(false) }

    // Pinned at the trailing edge normally; once the drag crosses the threshold
    // the trash button springs over to hug the row's trailing edge and follows
    // the finger — signalling "release to delete".
    val buttonLeftTarget = if (pastThreshold) {
        rowWidthPx + offsetPx
    } else {
        rowWidthPx - actionWidthPx
    }
    val buttonLeft by animateFloatAsState(buttonLeftTarget, label = "deleteButton")

    Box(modifier.onSizeChanged { rowWidthPx = it.width.toFloat() }) {
        // Revealed layer: the tappable delete action under the row.
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.error),
        ) {
            IconButton(
                onClick = {
                    settleBack()
                    onSwipeDelete()
                },
                modifier = Modifier
                    .offset { IntOffset(buttonLeft.roundToInt(), 0) }
                    .width(DELETE_ACTION_WIDTH)
                    .fillMaxHeight(),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.home_delete),
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    onDragStarted = { onSwipeStart() },
                    state = rememberDraggableState { delta ->
                        val target = (offsetPx + delta).coerceIn(-rowWidthPx, 0f)
                        val crossed = deleteThresholdPx > 0f && target <= -deleteThresholdPx
                        if (crossed && !pastThreshold) {
                            // Reject = the strongest buzz Compose exposes — this is the
                            // "you're about to delete" moment, make it unmissable.
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        }
                        pastThreshold = crossed
                        offsetPx = target
                    },
                    onDragStopped = {
                        val settleTo = when {
                            // Released past the threshold → ask to confirm right away.
                            pastThreshold -> {
                                onSwipeDelete()
                                0f
                            }

                            // Button fully uncovered but under the threshold → hold it revealed.
                            offsetPx <= -actionWidthPx -> {
                                -actionWidthPx
                            }

                            // Still (partly) covering the button → snap closed.
                            else -> {
                                0f
                            }
                        }
                        pastThreshold = false
                        animate(offsetPx, settleTo) { value, _ -> offsetPx = value }
                    },
                ),
        ) {
            row()
        }
    }
}

private fun previewContact() = Contact(
    contactId = 1L,
    displayName = "Amelia Hartwell",
    rawContacts = listOf(
        RawContact(
            rawContactId = 1L,
            accountType = "com.google",
            accountName = "rycco@gmail.com",
            organization = "Hartwell & Co",
            phones = listOf(LabeledValue(1L, "+44 7700 900312", "Mobile")),
        ),
        RawContact(rawContactId = 2L, accountType = "sim", accountName = "SIM 1"),
    ),
)

@Preview(name = "Contact row · light")
@Preview(name = "Contact row · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContactRowPreview() {
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ContactRow(
                previewContact(),
                extras = ContactRowExtras(
                    subtitle = "+44 7700 900312",
                    accountDots = listOf("com.google" to "amelia@gmail.com"),
                ),
                selected = false,
                swipeEnabled = true,
                onClick = {},
                onSwipeDelete = {},
            )
        }
    }
}
