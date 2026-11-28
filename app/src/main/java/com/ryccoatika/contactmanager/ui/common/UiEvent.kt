package com.ryccoatika.contactmanager.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** One-shot events ViewModels emit to their screen (the app's one event convention). */
sealed interface UiEvent {
    /** Transient, non-blocking feedback surfaced via the screen's snackbar host. */
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
    ) : UiEvent
}

/** Collects [events] into [snackbarHostState] for the composition's lifetime. */
@Composable
fun CollectUiEvents(events: Flow<UiEvent>, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    // A newer message replaces the one on screen: an Undo snackbar
                    // stays up long, and queueing behind it would delay the next
                    // action's feedback (and block the emitting ViewModel) for seconds.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    launch {
                        val result = snackbarHostState.showSnackbar(
                            message = event.message,
                            actionLabel = event.actionLabel,
                            duration = if (event.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
                    }
                }
            }
        }
    }
}
