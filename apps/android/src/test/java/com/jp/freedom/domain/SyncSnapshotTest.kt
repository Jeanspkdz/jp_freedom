package com.jp.freedom.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyncSnapshotTest {
    @Test
    fun desktopSiteBlocklistStartsWithTheFiveSelectedDefaults() {
        assertEquals(
            listOf("facebook.com", "instagram.com", "tiktok.com", "x.com", "reddit.com"),
            DEFAULT_DESKTOP_BLOCKED_DOMAINS,
        )
        assertTrue(DesktopSiteBlocklist.validate(DEFAULT_DESKTOP_BLOCKED_DOMAINS) is BlocklistValidation.Valid)
        assertTrue("web.whatsapp.com" !in DEFAULT_DESKTOP_BLOCKED_DOMAINS)
    }

    @Test
    fun staleRevisionMergeKeepsPendingLocalFieldsAndImportsUnrelatedRemoteFields() {
        val date = LocalDate.of(2026, 11, 5)
        val localBlock = PlannedBlock(
            id = "local-block",
            date = date,
            name = "Local edit",
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
        )
        val remoteBlock = PlannedBlock(
            id = "remote-block",
            date = date,
            name = "Remote block",
            startTime = LocalTime.of(11, 0),
            endTime = LocalTime.of(12, 0),
        )
        val local = SyncSnapshot(
            settings = FocusSettings(requiredBlockCount = 4),
            desktopBlockedDomains = listOf("facebook.com", "reddit.com"),
            blocks = listOf(localBlock),
        )
        val remote = SyncSnapshot(
            settings = FocusSettings(requiredBlockCount = 3, eveningCutoff = LocalTime.of(22, 0)),
            desktopBlockedDomains = listOf("facebook.com", "instagram.com"),
            blocks = listOf(remoteBlock),
        )

        val result = SyncSnapshotReconciler.merge(
            remote = remote,
            local = local,
            pendingPaths = setOf(
                "settings.requiredBlockCount",
                "desktopBlockedDomains.reddit.com",
                "blocks.local-block.name",
            ),
        )

        assertEquals(4, result.snapshot.settings.requiredBlockCount)
        assertEquals(LocalTime.of(22, 0), result.snapshot.settings.eveningCutoff)
        assertEquals(listOf("facebook.com", "instagram.com", "reddit.com"), result.snapshot.desktopBlockedDomains)
        assertEquals(setOf("local-block", "remote-block"), result.snapshot.blocks.map(PlannedBlock::id).toSet())
        assertEquals(
            setOf("settings.requiredBlockCount", "desktopBlockedDomains.reddit.com"),
            result.conflictedPaths,
        )
        assertEquals(
            mapOf("settings.requiredBlockCount" to ("3" to "4"), "desktopBlockedDomains.reddit.com" to ("excluded" to "included")),
            result.fieldConflicts.associate { it.path to (it.remoteValue to it.localValue) },
        )
    }

    @Test
    fun concurrentOfflineSessionStartsRemainAvailableAsCandidates() {
        val phone = SharedStudySession(
            "phone-start", "Read", Instant.parse("2026-11-05T10:00:00Z"),
            Instant.parse("2026-11-05T11:00:00Z"), AccessProfile.ESSENTIALS_ONLY,
        )
        val chrome = phone.copy(
            id = "chrome-start", taskName = "Write",
            startedAt = Instant.parse("2026-11-05T10:01:00Z"),
            endsAt = Instant.parse("2026-11-05T11:01:00Z"),
        )

        val result = SyncSnapshotReconciler.merge(
            remote = SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, emptyList(), activeStudySession = phone),
            local = SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, emptyList(), activeStudySession = chrome),
            pendingPaths = setOf("activeStudySession"),
        )

        assertEquals(phone, result.snapshot.activeStudySession)
        assertEquals(setOf("phone-start", "chrome-start"), result.snapshot.sessionStartCandidates.map(SharedStudySession::id).toSet())
        assertEquals(setOf("activeStudySession"), result.conflictedPaths)
    }

    @Test
    fun staleRevisionMergeKeepsLocalBlockNameAndAcceptsRemoteCompletionStatus() {
        val date = LocalDate.of(2026, 11, 5)
        val local = PlannedBlock(
            id = "same-block",
            date = date,
            name = "Phone edit",
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
        )
        val remote = local.copy(name = "Server edit", completionStatus = CompletionStatus.FINISHED)
        val result = SyncSnapshotReconciler.merge(
            remote = SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, listOf(remote)),
            local = SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, listOf(local)),
            pendingPaths = setOf("blocks.same-block.name"),
        )

        assertEquals("Phone edit", result.snapshot.blocks.single().name)
        assertEquals(CompletionStatus.FINISHED, result.snapshot.blocks.single().completionStatus)
        assertEquals(setOf("blocks.same-block.name"), result.conflictedPaths)
    }

    @Test
    fun staleRevisionMergePreservesPendingLocalNoteAndImportsRemoteNote() {
        val date = LocalDate.of(2026, 11, 5)
        val base = PlannedBlock(
            id = "same-block",
            date = date,
            name = "Study",
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
        )
        val remote = base.copy(note = "Remote note")
        val local = base.copy(note = "Phone note")

        val pendingNote = SyncSnapshotReconciler.merge(
            remote = SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, listOf(remote)),
            local = SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, listOf(local)),
            pendingPaths = setOf("blocks.same-block.note"),
        )
        val uneditedNote = SyncSnapshotReconciler.merge(
            remote = SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, listOf(remote)),
            local = SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, listOf(base)),
            pendingPaths = emptySet(),
        )

        assertEquals("Phone note", pendingNote.snapshot.blocks.single().note)
        assertEquals(setOf("blocks.same-block.note"), pendingNote.conflictedPaths)
        assertEquals("Remote note", uneditedNote.snapshot.blocks.single().note)
    }

    @Test
    fun replacingTheSyncEndpointSeedsThePhoneSnapshotInsteadOfImportingAnotherEndpoint() {
        val local = SyncSnapshot(FocusSettings(requiredBlockCount = 5), listOf("reddit.com"), emptyList())
        val remote = SyncSnapshot(FocusSettings(requiredBlockCount = 2), listOf("facebook.com"), emptyList())

        val result = SyncSnapshotReconciler.merge(remote, local, setOf("snapshot.replaceAll"))

        assertEquals(local, result.snapshot)
        assertTrue(result.conflictedPaths.isEmpty())
    }

    @Test
    fun firstHistoryImportKeepsPhoneAndServerSessionsAndReportsConflictingValues() {
        val phoneSession = historySession("same-id", "Phone version")
        val serverSession = historySession("same-id", "Server version")
        val phoneOnly = historySession("phone-only", "Phone only")
        val serverOnly = historySession("server-only", "Server only")

        val result = SyncSnapshotReconciler.merge(
            remote = SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, emptyList(), studyHistory = listOf(serverSession, serverOnly)),
            local = SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, emptyList(), studyHistory = listOf(phoneSession, phoneOnly)),
            pendingPaths = setOf("studyHistory.import"),
        )

        assertEquals(setOf("same-id", "same-id-phone-copy", "phone-only", "server-only"), result.snapshot.studyHistory.map { it.id }.toSet())
        assertEquals("Server version", result.snapshot.studyHistory.first { it.id == "same-id" }.taskName)
        assertEquals("Phone copy · Phone version", result.snapshot.studyHistory.first { it.id == "same-id-phone-copy" }.taskName)
        assertEquals(setOf("studyHistory.same-id"), result.conflictedPaths)

        val repeatedImport = SyncSnapshotReconciler.merge(result.snapshot, result.snapshot, emptySet())
        assertEquals(4, repeatedImport.snapshot.studyHistory.size)
        assertEquals(4, repeatedImport.snapshot.studyHistory.map { it.id }.toSet().size)
    }

    @Test
    fun explicitHistoryClearDoesNotChangePlansOrDesktopSites() {
        val block = PlannedBlock("plan-1", LocalDate.of(2026, 11, 5), "Study", LocalTime.of(9, 0), LocalTime.of(10, 0))
        val remote = SyncSnapshot(FocusSettings(), listOf("reddit.com"), listOf(block), studyHistory = listOf(historySession("session", "Study")))
        val local = remote.copy(studyHistory = emptyList())

        val result = SyncSnapshotReconciler.merge(remote, local, setOf("studyHistory.clear"))

        assertTrue(result.snapshot.studyHistory.isEmpty())
        assertEquals(listOf(block), result.snapshot.blocks)
        assertEquals(listOf("reddit.com"), result.snapshot.desktopBlockedDomains)
    }

    @Test
    fun choosingOneVersionOfAnImportConflictRemovesOnlyTheRejectedSession() {
        val server = historySession("same-id", "Server version")
        val phone = historySession("same-id", "Phone version")
        val imported = SyncSnapshotReconciler.merge(
            SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, emptyList(), studyHistory = listOf(server)),
            SyncSnapshot(FocusSettings(), DEFAULT_DESKTOP_BLOCKED_DOMAINS, emptyList(), studyHistory = listOf(phone)),
            setOf("studyHistory.import"),
        ).snapshot

        val keepPhone = SyncSnapshotReconciler.merge(
            imported,
            imported.copy(studyHistory = imported.studyHistory.filterNot { it.id == "same-id" }),
            setOf("studyHistory.same-id.deleted"),
        )
        assertEquals(listOf("same-id-phone-copy"), keepPhone.snapshot.studyHistory.map { it.id })
    }

    private fun historySession(id: String, task: String) = StudyTimerSession(
        id = id,
        taskName = task,
        durationMillis = 3_600_000,
        accessProfile = AccessProfile.ESSENTIALS_ONLY,
        startedAt = Instant.parse("2026-11-05T10:00:00Z"),
        endedAt = Instant.parse("2026-11-05T11:00:00Z"),
        actualMillis = 3_600_000,
        state = StudySessionState.REVIEWED,
        completionStatus = CompletionStatus.FINISHED,
    )
}
