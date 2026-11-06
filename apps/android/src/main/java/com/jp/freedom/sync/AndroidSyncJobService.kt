package com.jp.freedom.sync

import android.app.job.JobParameters
import android.app.job.JobService
import com.jp.freedom.data.FocusRepository
import com.jp.freedom.domain.SyncSnapshotReconciler
import com.jp.freedom.enforcement.PolicyAlarmScheduler

class AndroidSyncJobService : JobService() {
    @Volatile private var stopped = false
    @Volatile private var runningThread: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        runningThread = Thread({
            val retry = runCatching { runSync(applicationContext) }
                .onFailure { error -> FocusRepository(applicationContext).markSyncError(error.userMessage()) }
                .getOrDefault(true)
            if (!stopped) jobFinished(params, retry)
        }, "jp-freedom-sync").apply { start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        runningThread?.interrupt()
        return true
    }

    private fun Throwable.userMessage(): String = when (this) {
        is SyncFailure -> message ?: "No se pudo sincronizar. Los cambios siguen guardados en este teléfono."
        else -> "Ocurrió un error al sincronizar. Los cambios siguen guardados en este teléfono; vuelve a intentar."
    }

    companion object {
        private val syncLock = Any()

        /** Runs the same sync engine as the background job, without waiting for JobScheduler. */
        fun syncNow(context: android.content.Context): Boolean = runSync(context.applicationContext)

        private fun runSync(context: android.content.Context): Boolean = synchronized(syncLock) {
            SyncEngine(context).run()
        }
    }
}

private class SyncEngine(private val context: android.content.Context) {
    fun run(): Boolean {
        val configuration = SyncConfigurationStore(context).configuration()
        if (configuration.endpoint.isBlank() || !configuration.hasWriteToken) {
            FocusRepository(context).markNotConfigured()
            return false
        }
        val token = try {
            SyncConfigurationStore(context).writeToken()
        } catch (error: Exception) {
            throw SyncFailure("No se pudo leer la clave protegida. Vuelve a ingresar el token de escritura.", error)
        } ?: run {
            FocusRepository(context).markNotConfigured()
            return false
        }
        val repository = FocusRepository(context)
        val api = SyncApiClient(configuration.endpoint, token)
        repository.markSyncing()

        var conflictResolved = false
        repeat(MAX_ATTEMPTS) {
            val local = repository.syncBundle()
            val server = api.getSnapshot()
            var current = local
            if (server.revision != local.state.revision) {
                val merge = SyncSnapshotReconciler.merge(
                    remote = server.snapshot,
                    local = local.snapshot,
                    pendingPaths = local.state.pendingPaths,
                )
                val pendingConflict = local.state.pending &&
                    (local.state.revision >= 0 || merge.conflictedPaths.isNotEmpty())
                if (pendingConflict) conflictResolved = true
                val applied = repository.setReconciledSnapshot(
                    snapshot = merge.snapshot,
                    revision = server.revision,
                    conflictResolved = conflictResolved,
                    expectedGeneration = local.state.generation,
                )
                if (!applied) return@repeat
                PolicyAlarmScheduler.scheduleNext(context)
                PolicyAlarmScheduler.broadcastRecheck(context)
                current = repository.syncBundle()
            }

            if (!current.state.pending) {
                repository.markSynced(server.revision, current.state.generation, conflictResolved)
                return false
            }

            when (val result = api.putSnapshot(server.revision, current.snapshot)) {
                is PutSnapshotResult.Accepted -> {
                    repository.markSynced(result.revision, current.state.generation, conflictResolved)
                    return false
                }
                PutSnapshotResult.RevisionConflict -> {
                    conflictResolved = true
                    repository.markSyncing()
                }
            }
        }
        repository.markSyncError("El servidor cambió varias veces durante la sincronización. Los cambios locales se conservaron; vuelve a intentar.")
        return true
    }

    companion object {
        private const val MAX_ATTEMPTS = 4
    }
}
