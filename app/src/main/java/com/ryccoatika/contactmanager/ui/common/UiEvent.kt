package com.ryccoatika.contactmanager.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/** One-shot events ViewModels emit to their screen (the app's one event convention). */
sealed interface UiEvent {
    /** Transient, non-blocking feedback surfaced via the screen's snackbar host. */
    data class ShowSnackbar(
        val message: String,
    ) : UiEvent
}

/** Collects [events] into [snackbarHostState] for the composition's lifetime. */
@Composable
fun CollectUiEvents(events: Flow<UiEvent>, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
}
