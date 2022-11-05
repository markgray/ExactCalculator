package com.example.calculator2.util

import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A coroutine version of `AsyncTask`
 * TODO: make methods and comments more closely resemble AsyncTask
 */
abstract class CoroutinesAsyncTask<Params, Progress, Result> {

    /**
     * The status of this Async Task
     */
    enum class Status {
        /**
         * Not yet running.
         */
        PENDING,

        /**
         * Task is running.
         */
        RUNNING,

        /**
         * Task has finished
         */
        FINISHED
    }

    /**
     * The status of this [CoroutinesAsyncTask] instance, one of [Status.PENDING], [Status.RUNNING],
     * or [Status.FINISHED]
     */
    var status: Status = Status.PENDING

    /**
     * Override this method to perform a computation on a background thread. The specified
     * parameters are the parameters passed to [execute] by the caller of this task.
     * This method can call [publishProgress] to publish updates on the UI thread.
     *
     * @param params The parameters of the task.
     */
    abstract fun doInBackground(vararg params: Params?): Result?

    /**
     * Runs on the UI thread after [publishProgress] is invoked. The specified values are the
     * values passed to [publishProgress]. The default version does nothing.
     *
     * @param progress The values indicating progress.
     */
    open fun onProgressUpdate(vararg progress: Progress?) {}

    /**
     * Runs on the UI thread after [doInBackground]. The specified result is the value returned
     * by [doInBackground]. The default version does nothing.
     *
     * This method won't be invoked if the task was cancelled.
     *
     * @param result The result of the operation computed by [doInBackground].
     */
    open fun onPostExecute(result: Result?) {}

    /**
     * Runs on the UI thread before [doInBackground]. Invoked directly by [execute] or
     * `executeOnExecutor`. The default version does nothing.
     */
    open fun onPreExecute() {}

    /**
     * Runs on the UI thread after [cancel] is invoked and [doInBackground] has finished.
     *
     * @param result The result, if any, computed in [doInBackground], can be `null`.
     */
    open fun onCancelled(result: Result?) {}

    /**
     * Returns `true` if this task was cancelled before it completed normally. If you are calling
     * [cancel] on the task, the value returned by this method should be checked periodically from
     * [doInBackground] to end the task as soon as possible.
     *
     * @return `true` if task was cancelled before it completed
     */
    var isCancelled: Boolean = false

    /**
     * Executes the [doInBackground] task with the specified parameters. This method must be
     * invoked on the UI thread.
     *
     * @param params The parameters of the task.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun execute(vararg params: Params) {

        when (status) {
            Status.RUNNING -> throw IllegalStateException(
                "Cannot execute task:"
                    + " the task is already running."
            )
            Status.FINISHED -> throw IllegalStateException(
                "Cannot execute task:"
                    + " the task has already been executed "
                    + "(a task can be executed only once)"
            )
            Status.PENDING -> Unit
        }

        status = Status.RUNNING

        // it can be used to setup UI - it should have access to Main Thread
        GlobalScope.launch(Dispatchers.Main) {
            onPreExecute()
        }

        // doInBackground works on background thread(default)
        GlobalScope.launch(Dispatchers.Default) {
            val result = doInBackground(*params)
            status = Status.FINISHED
            withContext(Dispatchers.Main) {
                // onPostExecute works on main thread to show output
                Log.d("Alpha", "after do in back " + status.name + "--" + isCancelled)
                if (!isCancelled) {
                    onPostExecute(result)
                }
            }
        }
    }

    /**
     * Attempts to cancel execution of this task. This attempt will fail if the task has already
     * completed, already been cancelled, or could not be cancelled for some other reason. If
     * successful, and this task has not started when [cancel] is called, this task should never
     * run. If the task has already started, then the [mayInterruptIfRunning] parameter determines
     * whether the thread executing this task should be interrupted in an attempt to stop the task.
     *
     * Calling this method will result in [onCancelled] being invoked on the UI thread after
     * [doInBackground] returns. Calling this method guarantees that [onPostExecute] is never
     * subsequently invoked. To finish the task as early as possible, check [isCancelled]
     * periodically from [doInBackground].
     *
     * @param mayInterruptIfRunning `<tt></tt>` if the thread executing this task should be
     * interrupted; otherwise, in-progress tasks are allowed to complete.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("MemberVisibilityCanBePrivate") // I like to use kdoc [] references
    fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (!mayInterruptIfRunning) Log.d("cancel", "cancel called with mayInterruptIfRunning false")
        isCancelled = true
        status = Status.FINISHED
        GlobalScope.launch(Dispatchers.Main) {
            // onPostExecute works on main thread to show output
            Log.d("Alpha", "after cancel " + status.name + "--" + isCancelled)
            onPostExecute(null)
        }
        return true
    }

    /**
     * This method can be invoked from [doInBackground] to publish updates on the UI thread while
     * the background computation is still running. Each call to this method will trigger the
     * execution of [onProgressUpdate] on the UI thread. [onProgressUpdate] will not be called
     * if the task has been canceled.
     *
     * @param progress The progress values to update the UI with.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("MemberVisibilityCanBePrivate") // I like to use kdoc [] references
    fun publishProgress(vararg progress: Progress) {
        //need to update main thread
        GlobalScope.launch(Dispatchers.Main) {
            if (!isCancelled) {
                onProgressUpdate(*progress)
            }
        }
    }
}
