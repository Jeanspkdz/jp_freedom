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

        fun <T> choose(path: String, remoteValue: T, localValue: T): T {
            if (path !in pendingPaths) return remoteValue
            if (remoteValue != localValue) conflicts += path
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
                if ((domain in remoteDomains) != (domain in localDomains)) conflicts += path
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
                else -> mergeBlock(remoteBlock, localBlock, pendingPaths, conflicts)
            }
        }.sortedWith(compareBy<PlannedBlock>({ it.date }, { it.startTime }, { it.id }))

        return SnapshotMergeResult(
            snapshot = SyncSnapshot(
                settings = settings,
                desktopBlockedDomains = domains,
                blocks = blocks,
                activeStudySession = choose(
                    "activeStudySession",
                    remote.activeStudySession,
                    local.activeStudySession,
                ),
            ),
            conflictedPaths = conflicts,
        )
    }

    private fun mergeBlock(
        remote: PlannedBlock,
        local: PlannedBlock,
        pendingPaths: Set<String>,
        conflicts: MutableSet<String>,
    ): PlannedBlock {
        fun <T> field(name: String, remoteValue: T, localValue: T): T {
            val path = "blocks.${local.id}.$name"
            if (path !in pendingPaths) return remoteValue
            if (remoteValue != localValue) conflicts += path
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
}
