package com.jp.freedom.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
