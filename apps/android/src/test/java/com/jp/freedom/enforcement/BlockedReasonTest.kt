package com.jp.freedom.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockedReasonTest {
    @Test
    fun reasonsUseStableIntentIds() {
        assertEquals("focus_block", BlockedReason.FOCUS_BLOCK.toIntentId())
        assertEquals("site_filter_setup", BlockedReason.SITE_FILTER_SETUP.toIntentId())
    }

    @Test
    fun stableIntentIdsRestoreTheirBlockedReasons() {
        assertEquals(BlockedReason.FOCUS_BLOCK, BlockedReason.fromIntentId("focus_block"))
        assertNull(BlockedReason.fromIntentId("evening_restriction"))
        assertEquals(BlockedReason.SITE_FILTER_SETUP, BlockedReason.fromIntentId("site_filter_setup"))
    }

    @Test
    fun unknownOrMissingIntentIdsDoNotBecomeBlockedReasons() {
        assertNull(BlockedReason.fromIntentId("future_reason"))
        assertNull(BlockedReason.fromIntentId(null))
    }
}
