package com.ryccoatika.contactmanager.data

import com.ryccoatika.contactmanager.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class BatchProgress(
    val done: Int,
    val total: Int,
    val label: String,
    val finished: Boolean,
    val error: String? = null,
)

/**
 * Long batch operations that survive ViewModel/config death. One operation at
 * a time; observable via [progress] and cancellable via [cancel]. ViewModels
 * depend on this seam (fakeable in JVM tests), never on the impl below.
 */
interface BatchRunner {
    val progress: StateFlow<BatchProgress?>

    /** Starts the move; false when another batch is still running (nothing started). */
    fun moveContacts(
        rawContactIds: List<Long>,
        targetType: String?,
        targetName: String?,
        label: String,
    ): Boolean

    fun cancel()

    fun clearFinished()
}

/** Runs batches in the application scope so they outlive the screen. */
@Singleton
class BatchOperationManager
    @Inject
    constructor(
        private val writer: ContactsWriter,
        @ApplicationScope private val scope: CoroutineScope,
    ) : BatchRunner {
        private val _progress = MutableStateFlow<BatchProgress?>(null)
        override val progress: StateFlow<BatchProgress?> = _progress.asStateFlow()

        private var job: Job? = null

        override fun moveContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            label: String,
        ): Boolean {
            if (job?.isActive == true) return false
            _progress.value = BatchProgress(0, rawContactIds.size, label, finished = false)
            job = scope.launch {
                val result = writer.moveRawContacts(rawContactIds, targetType, targetName) { done, total ->
                    _progress.value = BatchProgress(done, total, label, finished = false)
                }
                _progress.value = BatchProgress(
                    done = rawContactIds.size,
                    total = rawContactIds.size,
                    label = label,
                    finished = true,
                    error = (result as? ContactOpResult.Failure)?.message,
                )
            }
            return true
        }

        override fun cancel() {
            job?.cancel()
            _progress.value = null
        }

        override fun clearFinished() {
            if (_progress.value?.finished == true) _progress.value = null
        }
    }
