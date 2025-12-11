package com.okta.directauth.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job

/**
 * State holder for managing cancellable jobs in Compose.
 */
class CancellableJobState {
    private var _job by mutableStateOf<Job?>(null)

    /**
     * Returns true if a job is currently active.
     */
    val isActive by derivedStateOf { _job?.isActive == true }

    /**
     * Returns the current job, or null if no job is active.
     */
    val job: Job? by derivedStateOf { _job }

    /**
     * Adds a new job.
     */
    fun addJob(newJob: Job) {
        _job = newJob
    }

    /**
     * Cancels the current job if it's active.
     */
    fun cancel() {
        if (_job?.isActive == true) _job?.cancel()
        _job = null
    }

    /**
     * Internal method to clear job reference after completion.
     */
    internal fun clearJob() {
        _job = null
    }
}

/**
 * Remember a [CancellableJobState] that automatically manages job lifecycle.
 *
 * This composable:
 * - Tracks the current job state
 * - Automatically clears job reference when job completes
 * - Provides methods to execute and cancel jobs
 *
 * Usage example:
 * ```
 * val jobState = rememberCancellableJob()
 *
 * Button(
 *     onClick = { jobState.execute(viewModel.doSomething()) },
 *     enabled = !jobState.isActive
 * ) {
 *     Text("Submit")
 * }
 *
 * // Cancel job when navigating away
 * onBackPressed = {
 *     jobState.cancel()
 *     navigateBack()
 * }
 * ```
 */
@Composable
fun rememberCancellableJob(): CancellableJobState {
    val jobState = remember { CancellableJobState() }

    // Automatically clear job reference when job completes
    jobState.job?.let { job ->
        LaunchedEffect(job) {
            job.join()
            jobState.clearJob()
        }
    }

    return jobState
}
