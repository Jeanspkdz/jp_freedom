package com.jp.freedom.sync

import android.content.Context
import com.jp.freedom.data.FocusRepository
import com.jp.freedom.domain.StudySessionState
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.enforcement.PolicyAlarmScheduler
import org.json.JSONObject

/** Reconciles the latest shared study timer without replacing the day-plan snapshot. */
internal class StudySessionSync(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("study_session_sync", Context.MODE_PRIVATE)

    fun resolveReviewConflict(api: SyncApiClient, repository: FocusRepository, keepPhone: Boolean) {
        val remoteById = api.getStudyHistory().associateBy { it.id }
        val pair = repository.studySessions().firstNotNullOfOrNull { local ->
            val remote = remoteById[local.id]
            if (remote != null && local.state == StudySessionState.REVIEWED
                && remote.state == StudySessionState.REVIEWED && fingerprint(local) != fingerprint(remote)) {
                local to remote
            } else null
        } ?: return
        if (keepPhone) api.resolveStudyHistory(pair.first)
        else repository.saveStudySession(pair.second)
    }

    fun run(api: SyncApiClient, repository: FocusRepository) {
        // Import existing phone history once, then pull completed sessions from other devices.
        val remoteHistory = api.getStudyHistory()
        val localHistory = repository.studySessions()
        val localIds = localHistory.mapTo(mutableSetOf()) { it.id }
        val remoteById = remoteHistory.associateBy { it.id }
        val reviewConflict = localHistory.firstOrNull { local ->
            val serverCopy = remoteById[local.id]
            serverCopy != null && local.state == StudySessionState.REVIEWED
                && serverCopy.state == StudySessionState.REVIEWED
                && fingerprint(local) != fingerprint(serverCopy)
        }
        if (reviewConflict != null) throw SyncFailure(
            "The review for ${reviewConflict.id} changed on both devices. Both versions were kept; choose which review to use.",
        )
        api.importStudyHistory(localHistory.filter { local ->
            val serverCopy = remoteById[local.id]
            serverCopy == null || (local.state == StudySessionState.REVIEWED && serverCopy.state != StudySessionState.REVIEWED)
        })
        for (past in remoteHistory) {
            if (past.id !in localIds && past.state == StudySessionState.REVIEWED) repository.saveStudySession(past)
        }
        val remote = api.getStudySession()
        val local = repository.activeStudySession() ?: repository.pendingStudyReview()
            ?: repository.studySessions().firstOrNull()
        val server = remote.session
        val lastId = preferences.getString("id", null)
        val lastFingerprint = preferences.getString("fingerprint", null)

        if (server == null) {
            if (local != null) publish(api, remote.revision, local)
            return
        }
        if (local == null) {
            receive(repository, server, remote.revision)
            return
        }
        if (local.id != server.id) {
            if (local.id in remote.supersededAttemptIds) {
                val stopped = if (local.state == StudySessionState.ACTIVE) {
                    com.jp.freedom.domain.StudyTimerPolicy.stop(local, java.time.Instant.now())
                } else local
                repository.saveStudySession(stopped.copy(
                    state = StudySessionState.REVIEWED,
                    outcome = com.jp.freedom.domain.SessionOutcome.NOT_FINISHED,
                    note = "Ended after choosing another offline timer",
                ))
                receive(repository, server, remote.revision)
                return
            }
            val reviewedPrevious = repository.studySessions().any { it.id == server.id && it.state == StudySessionState.REVIEWED }
            if (local.state == StudySessionState.ACTIVE && (server.state == StudySessionState.REVIEWED || reviewedPrevious)) {
                publish(api, remote.revision, local)
                return
            }
            if (local.state == StudySessionState.REVIEWED && server.state == StudySessionState.REVIEWED
                && local.startedAt.isAfter(server.startedAt)) {
                publish(api, remote.revision, local)
                return
            }
            if (local.state == StudySessionState.ACTIVE || local.state == StudySessionState.PENDING_REVIEW || local.state == StudySessionState.BREAKING) {
                throw SyncFailure("Two study sessions were started on different devices. Both were kept; choose which to continue.")
            }
            receive(repository, server, remote.revision)
            return
        }

        val localFingerprint = fingerprint(local)
        val remoteFingerprint = fingerprint(server)
        if (localFingerprint == remoteFingerprint) {
            remember(server, remote.revision)
        } else if (lastId == local.id && localFingerprint == lastFingerprint) {
            receive(repository, server, remote.revision)
        } else if (lastId == local.id && remoteFingerprint == lastFingerprint) {
            publish(api, remote.revision, local)
        } else {
            throw SyncFailure("The study session changed on both devices. Both changes were kept; resolve the conflict before syncing again.")
        }
    }

    private fun receive(repository: FocusRepository, session: StudyTimerSession, revision: Long) {
        repository.saveStudySession(session)
        remember(session, revision)
        PolicyAlarmScheduler.scheduleNext(appContext)
        PolicyAlarmScheduler.broadcastRecheck(appContext)
    }

    private fun publish(api: SyncApiClient, revision: Long, session: StudyTimerSession) {
        when (val result = api.putStudySession(revision, session)) {
            is PutSnapshotResult.Accepted -> remember(session, result.revision)
            PutSnapshotResult.RevisionConflict -> throw SyncFailure("The study session changed on another device. Sync again to resolve it.")
        }
    }

    private fun remember(session: StudyTimerSession, revision: Long) {
        preferences.edit().putString("id", session.id).putString("fingerprint", fingerprint(session))
            .putLong("revision", revision).apply()
    }

    private fun fingerprint(session: StudyTimerSession): String = JSONObject().apply {
        put("id", session.id); put("state", session.state.name); put("startedAt", session.startedAt.toString())
        put("allowedAppsListId", session.allowedAppsListId)
        put("allowedAppsListName", session.allowedAppsListName)
        put("allowedAppPackages", org.json.JSONArray(session.allowedAppPackages.sorted()))
        put("durationMillis", session.durationMillis); put("endedAt", session.endedAt?.toString())
        put("actualMillis", session.actualMillis); put("taskName", session.taskName)
        put("outcome", session.outcome?.name); put("note", session.note); put("remaining", session.remaining)
        put("breakStartedAt", session.breakStartedAt?.toString()); put("breakDurationMillis", session.breakDurationMillis)
        put("nextTask", session.nextTask); put("focusAreaId", session.focusAreaId)
    }.toString()
}
