package com.jp.freedom.domain

val DEFAULT_DESKTOP_BLOCKED_DOMAINS = listOf(
    "facebook.com",
    "instagram.com",
    "tiktok.com",
    "x.com",
    "reddit.com",
)

sealed interface BlocklistValidation {
    data object Valid : BlocklistValidation
    data object ActiveBlockLocked : BlocklistValidation
    data object TooManyDomains : BlocklistValidation
    data object DuplicateDomain : BlocklistValidation
    data class InvalidDomain(val domain: String) : BlocklistValidation
}
object DesktopSiteBlocklist {
    private val domainPattern = Regex("^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")

    fun validate(domains: List<String>): BlocklistValidation {
        if (domains.size > 100) return BlocklistValidation.TooManyDomains
        if (domains.size != domains.toSet().size) return BlocklistValidation.DuplicateDomain
        domains.firstOrNull { !domainPattern.matches(it) }?.let {
            return BlocklistValidation.InvalidDomain(it)
        }
        return BlocklistValidation.Valid
    }

    fun normalize(input: String): String = input.trim().lowercase()
}

data class SyncSnapshot(
    val settings: FocusSettings,
    val desktopBlockedDomains: List<String>,
    val blocks: List<PlannedBlock>,
    val activeStudySession: SharedStudySession? = null,
    val phoneReceivedStudySessionId: String? = null,
    val pendingStudyReviewSessionId: String? = null,
    val studyHistory: List<StudyTimerSession> = emptyList(),
    val sessionStartCandidates: List<SharedStudySession> = emptyList(),
    val fieldConflicts: List<SyncFieldConflict> = emptyList(),
)

data class SharedStudySession(
    val id: String,
    val taskName: String,
    val startedAt: java.time.Instant,
    val endsAt: java.time.Instant,
    val accessProfile: AccessProfile,
)

data class SnapshotMergeResult(
    val snapshot: SyncSnapshot,
    val conflictedPaths: Set<String>,
    val fieldConflicts: List<SyncFieldConflict> = emptyList(),
)

data class SyncFieldConflict(
    val path: String,
    val remoteValue: String,
    val localValue: String,
)

object SyncSnapshotReconciler {
    fun merge(
        remote: SyncSnapshot,
        local: SyncSnapshot,
        pendingPaths: Set<String>,
    ): SnapshotMergeResult {
        if ("snapshot.replaceAll" in pendingPaths) {
            return SnapshotMergeResult(snapshot = local, conflictedPaths = emptySet())
        }
        val conflicts = mutableSetOf<String>()
        val fieldConflicts = linkedMapOf<String, SyncFieldConflict>()

        fun <T> choose(path: String, remoteValue: T, localValue: T): T {
            if (path !in pendingPaths) return remoteValue
            val explicitlyResolved = "fieldConflicts.$path.resolved" in pendingPaths
            if (remoteValue != localValue) {
                if (!explicitlyResolved) conflicts += path
                if (!explicitlyResolved && isChoiceField(path)) {
                    fieldConflicts[path] = SyncFieldConflict(path, conflictValue(remoteValue), conflictValue(localValue))
                }
            }
            return localValue
        }

        val settings = FocusSettings(
            requiredBlockCount = choose(
                "settings.requiredBlockCount",
                remote.settings.requiredBlockCount,
                local.settings.requiredBlockCount,
            ),
            eveningCutoff = choose(
                "settings.eveningCutoff",
                remote.settings.eveningCutoff,
                local.settings.eveningCutoff,
            ),
        )

        val remoteDomains = remote.desktopBlockedDomains.toSet()
        val localDomains = local.desktopBlockedDomains.toSet()
        val domains = (remoteDomains + localDomains).filter { domain ->
            val path = "desktopBlockedDomains.$domain"
            if (path in pendingPaths) {
                val explicitlyResolved = "fieldConflicts.$path.resolved" in pendingPaths
                if (!explicitlyResolved && (domain in remoteDomains) != (domain in localDomains)) {
                    conflicts += path
                    fieldConflicts[path] = SyncFieldConflict(
                        path,
                        if (domain in remoteDomains) "included" else "excluded",
                        if (domain in localDomains) "included" else "excluded",
                    )
                }
                domain in localDomains
            } else {
                domain in remoteDomains
            }
        }.sorted()

        val remoteBlocks = remote.blocks.associateBy(PlannedBlock::id)
        val localBlocks = local.blocks.associateBy(PlannedBlock::id)
        val allIds = remoteBlocks.keys + localBlocks.keys
        val blocks = allIds.mapNotNull { id ->
            val remoteBlock = remoteBlocks[id]
            val localBlock = localBlocks[id]
            val deletedPath = "blocks.$id.deleted"
            if (deletedPath in pendingPaths) {
                if (remoteBlock != null) conflicts += deletedPath
                return@mapNotNull null
            }
            when {
                remoteBlock == null && localBlock == null -> null
                remoteBlock == null -> {
                    if (localBlock != null && pendingPaths.any { it.startsWith("blocks.$id.") }) {
                        localBlock
                    } else {
                        null
                    }
                }
                localBlock == null -> {
                    if (pendingPaths.any { it.startsWith("blocks.$id.") }) {
                        remoteBlock
                    } else {
                        remoteBlock
                    }
                }
                else -> mergeBlock(remoteBlock, localBlock, pendingPaths, conflicts, fieldConflicts)
            }
        }.sortedWith(compareBy<PlannedBlock>({ it.date }, { it.startTime }, { it.id }))

        val competingStarts = if ("activeStudySession" in pendingPaths
            && remote.activeStudySession != null
            && local.activeStudySession != null
            && remote.activeStudySession != local.activeStudySession) {
            (remote.sessionStartCandidates + local.sessionStartCandidates + listOfNotNull(remote.activeStudySession, local.activeStudySession))
                .distinctBy(SharedStudySession::id)
                .take(2)
        } else {
            (remote.sessionStartCandidates + local.sessionStartCandidates).distinctBy(SharedStudySession::id).take(2)
        }
        val competingStartIds = competingStarts.map(SharedStudySession::id).toSet()
        val competingStartConflict = competingStarts.size > 1
            && remote.activeStudySession != null
            && local.activeStudySession != null
            && remote.activeStudySession.id in competingStartIds
            && local.activeStudySession.id in competingStartIds

        return SnapshotMergeResult(
            snapshot = SyncSnapshot(
                settings = settings,
                desktopBlockedDomains = domains,
                blocks = blocks,
                activeStudySession = if (competingStartConflict) remote.activeStudySession else choose(
                    "activeStudySession",
                    remote.activeStudySession,
                    local.activeStudySession,
                ),
                phoneReceivedStudySessionId = choose(
                    "phoneReceivedStudySessionId",
                    remote.phoneReceivedStudySessionId,
                    local.phoneReceivedStudySessionId,
                ),
                pendingStudyReviewSessionId = choose(
                    "pendingStudyReviewSessionId",
                    remote.pendingStudyReviewSessionId,
                    local.pendingStudyReviewSessionId,
                ),
                studyHistory = mergeStudyHistory(remote.studyHistory, local.studyHistory, pendingPaths, conflicts, fieldConflicts),
                sessionStartCandidates = competingStarts,
                fieldConflicts = (remote.fieldConflicts.filterNot { conflict ->
                    "fieldConflicts.${conflict.path}.resolved" in pendingPaths || conflict.path in fieldConflicts
                } + fieldConflicts.values).distinctBy(SyncFieldConflict::path),
            ),
            conflictedPaths = conflicts,
            fieldConflicts = fieldConflicts.values.toList(),
        )
    }

    private fun mergeStudyHistory(
        remote: List<StudyTimerSession>,
        local: List<StudyTimerSession>,
        pendingPaths: Set<String>,
        conflicts: MutableSet<String>,
        fieldConflicts: MutableMap<String, SyncFieldConflict>,
    ): List<StudyTimerSession> {
        if ("studyHistory.clear" in pendingPaths) return emptyList()
        val localById = local.associateBy(StudyTimerSession::id)
        val remoteById = remote.associateBy(StudyTimerSession::id)
        val merged = (remoteById.keys + localById.keys).mapNotNull { id ->
            if ("studyHistory.$id.deleted" in pendingPaths) return@mapNotNull null
            val serverSession = remoteById[id]
            val phoneSession = localById[id]
            if (serverSession != null && phoneSession != null && serverSession != phoneSession) {
                conflicts += "studyHistory.$id"
            }
            when {
                serverSession == null -> phoneSession
                phoneSession == null -> serverSession
                serverSession != phoneSession && "studyHistory.import" in pendingPaths -> serverSession
                "studyHistory.$id" in pendingPaths -> phoneSession
                else -> serverSession
            }
        }
        val conflictAlternates = (remoteById.keys intersect localById.keys).mapNotNull { id ->
            val serverSession = remoteById.getValue(id)
            val phoneSession = localById.getValue(id)
            if (serverSession == phoneSession || "studyHistory.import" !in pendingPaths) return@mapNotNull null
            val forkId = "${id.take(68)}-phone-copy"
            val conflictPath = "studyHistory.$id"
            fieldConflicts[conflictPath] = SyncFieldConflict(conflictPath, id, forkId)
            phoneSession.copy(id = forkId, taskName = "Phone copy · ${phoneSession.taskName}")
        }
        return (merged + conflictAlternates).distinctBy(StudyTimerSession::id).sortedByDescending(StudyTimerSession::startedAt)
    }

    private fun mergeBlock(
        remote: PlannedBlock,
        local: PlannedBlock,
        pendingPaths: Set<String>,
        conflicts: MutableSet<String>,
        fieldConflicts: MutableMap<String, SyncFieldConflict>,
    ): PlannedBlock {
        fun <T> field(name: String, remoteValue: T, localValue: T): T {
            val path = "blocks.${local.id}.$name"
            if (path !in pendingPaths) return remoteValue
            val explicitlyResolved = "fieldConflicts.$path.resolved" in pendingPaths
            if (remoteValue != localValue) {
                if (!explicitlyResolved) conflicts += path
                if (!explicitlyResolved && isChoiceField(path)) fieldConflicts[path] = SyncFieldConflict(path, conflictValue(remoteValue), conflictValue(localValue))
            }
            return localValue
        }

        return PlannedBlock(
            id = local.id,
            date = field("date", remote.date, local.date),
            name = field("name", remote.name, local.name),
            startTime = field("startTime", remote.startTime, local.startTime),
            endTime = field("endTime", remote.endTime, local.endTime),
            accessProfile = field("accessProfile", remote.accessProfile, local.accessProfile),
            completionStatus = field("completionStatus", remote.completionStatus, local.completionStatus),
            note = field("note", remote.note, local.note),
        )
    }

    private fun conflictValue(value: Any?): String = value?.toString() ?: "<null>"

    private fun isChoiceField(path: String): Boolean = path.startsWith("settings.")
        || path.startsWith("desktopBlockedDomains.")
        || path.startsWith("blocks.")
}
