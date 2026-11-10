package com.jp.freedom.enforcement

import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.DayPlanPolicy
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.domain.StudyTimerPolicy
import com.jp.freedom.domain.StudySessionState
import java.time.LocalDateTime

data class FocusPolicyState(
    val blockedReason: BlockedReason?,
    val activeBlock: PlannedBlock?,
    val accessProfile: AccessProfile? = activeBlock?.accessProfile,
)

object FocusPolicy {
    fun evaluate(
        now: LocalDateTime,
        blocks: List<PlannedBlock>,
        settings: FocusSettings,
        studySession: StudyTimerSession? = null,
    ): FocusPolicyState {
        if (studySession?.state == StudySessionState.ACTIVE && StudyTimerPolicy.finishIfDue(studySession, now.atZone(java.time.ZoneId.systemDefault()).toInstant()) == null) {
            return FocusPolicyState(BlockedReason.FOCUS_BLOCK, null, studySession.accessProfile)
        }
        val eveningRestriction = DayPlanPolicy.shouldRestrictEvening(
            now = now,
            cutoff = settings.eveningCutoff,
            studyHoursTargetMinutes = settings.studyHoursTargetMinutes,
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

        val profile = state.accessProfile
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
