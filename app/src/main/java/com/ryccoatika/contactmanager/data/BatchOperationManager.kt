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
    /** Snackbar text for a clean finish, resolved up-front by the caller. */
    val finishedMessage: String? = null,
)

/**
 * Long batch operations that survive ViewModel/config death. One operation at
 * a time; observable via [progress] and cancellable via [cancel]. ViewModels
 * depend on this seam (fakeable in JVM tests), never on the impl below.
 */
interface BatchRunner {
    val progress: StateFlow<BatchProgress?>

    /** Runs an arbitrary counted batch; false when another batch is running. */
    fun run(
        total: Int,
        label: String,
        finishedMessage: String,
        operation: suspend (onProgress: (Int, Int) -> Unit) -> ContactOpResult,
    ): Boolean

    /** Starts the move; false when another batch is still running (nothing started). */
    fun moveContacts(
        rawContactIds: List<Long>,
        targetType: String?,
        targetName: String?,
        label: String,
        finishedMessage: String,
    ): Boolean

    /** Starts the copy (sources untouched); false when another batch is running. */
    fun copyContacts(
        rawContactIds: List<Long>,
        targetType: String?,
        targetName: String?,
        label: String,
        finishedMessage: String,
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
            finishedMessage: String,
        ): Boolean = run(rawContactIds.size, label, finishedMessage) { onProgress ->
            writer.moveRawContacts(rawContactIds, targetType, targetName, onProgress)
        }

        override fun copyContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            label: String,
            finishedMessage: String,
        ): Boolean = run(rawContactIds.size, label, finishedMessage) { onProgress ->
            writer.copyRawContacts(rawContactIds, targetType, targetName, onProgress)
        }

        override fun run(
            total: Int,
            label: String,
            finishedMessage: String,
            operation: suspend (onProgress: (Int, Int) -> Unit) -> ContactOpResult,
        ): Boolean {
            if (job?.isActive == true) return false
            _progress.value = BatchProgress(0, total, label, finished = false)
            job = scope.launch {
                val result = operation { done, t ->
                    _progress.value = BatchProgress(done, t, label, finished = false)
                }
                _progress.value = BatchProgress(
                    done = total,
                    total = total,
                    label = label,
                    finished = true,
                    error = (result as? ContactOpResult.Failure)?.message,
                    finishedMessage = finishedMessage,
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
