package com.jp.freedom.enforcement

import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.DayPlanPolicy
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import java.time.LocalDateTime

data class FocusPolicyState(
    val blockedReason: BlockedReason?,
    val activeBlock: PlannedBlock?,
)

object FocusPolicy {
    fun evaluate(
        now: LocalDateTime,
        blocks: List<PlannedBlock>,
        settings: FocusSettings,
    ): FocusPolicyState {
        val eveningRestriction = DayPlanPolicy.shouldRestrictEvening(
            now = now,
            cutoff = settings.eveningCutoff,
            requiredBlockCount = settings.requiredBlockCount,
            blocks = blocks,
        )
        if (eveningRestriction) return FocusPolicyState(BlockedReason.EVENING_RESTRICTION, null)

        val activeBlock = blocks.firstOrNull { block ->
            block.date == now.toLocalDate() &&
                block.completionStatus != com.jp.freedom.domain.CompletionStatus.FINISHED &&
                !now.toLocalTime().isBefore(block.startTime) &&
                now.toLocalTime().isBefore(block.endTime)
        }
        return FocusPolicyState(
            blockedReason = if (activeBlock == null) null else BlockedReason.FOCUS_BLOCK,
            activeBlock = activeBlock,
        )
    }

    fun allowedPackages(
        state: FocusPolicyState,
        ownPackage: String,
        essentialPackages: Set<String>,
        siteFilterRunning: Boolean = false,
        defaultInputMethodPackage: String? = null,
    ): Set<String> {
        val allowed = (essentialPackages + ownPackage).toMutableSet()
        defaultInputMethodPackage?.takeIf(String::isNotBlank)?.let(allowed::add)
        if (state.blockedReason == BlockedReason.EVENING_RESTRICTION) return allowed

        val profile = state.activeBlock?.accessProfile
        when (profile) {
            AccessProfile.ESSENTIALS_WITH_CHATGPT -> allowed += CHATGPT_PACKAGE
            AccessProfile.ESSENTIALS_WITH_CHATGPT_AND_SEARCH -> {
                allowed += CHATGPT_PACKAGE
                allowed += GOOGLE_SEARCH_PACKAGE
            }
            AccessProfile.ESSENTIALS_ONLY, null -> Unit
        }

        if (profile != null && profile != AccessProfile.ESSENTIALS_ONLY &&
            state.blockedReason == BlockedReason.FOCUS_BLOCK && siteFilterRunning
        ) {
            allowed += CHROME_PACKAGE
        }
        return allowed
    }

    private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    private const val GOOGLE_SEARCH_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val CHROME_PACKAGE = "com.android.chrome"
}
