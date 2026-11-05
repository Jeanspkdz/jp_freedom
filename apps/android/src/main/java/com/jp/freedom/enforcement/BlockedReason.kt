package com.jp.freedom.enforcement

enum class BlockedReason(private val intentId: String) {
    FOCUS_BLOCK("focus_block"),
    EVENING_RESTRICTION("evening_restriction"),
    SITE_FILTER_SETUP("site_filter_setup");

    fun toIntentId(): String = intentId

    companion object {
        fun fromIntentId(intentId: String?): BlockedReason? =
            values().firstOrNull { it.intentId == intentId }
    }
}
