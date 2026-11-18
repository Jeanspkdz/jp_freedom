package com.jp.freedom.enforcement

import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.domain.StudyTimerPolicy
import com.jp.freedom.domain.StudySessionState
import java.time.LocalDateTime

data class FocusPolicyState(
    val blockedReason: BlockedReason?,
    val activeBlock: PlannedBlock?,
    // The legacy profile field is retained while old session records migrate.
    val accessProfile: AccessProfile? = activeBlock?.let { AccessProfile.ESSENTIALS_ONLY },
    val allowedPackageSnapshot: Set<String>? = null,
    val allowedAppPackages: Set<String> = emptySet(),
)

object FocusPolicy {
    fun evaluate(
        now: LocalDateTime,
        blocks: List<PlannedBlock>,
        settings: FocusSettings,
        studySession: StudyTimerSession? = null,
    ): FocusPolicyState {
        if (studySession?.state == StudySessionState.BREAKING &&
            StudyTimerPolicy.finishBreakIfDue(studySession, now.atZone(java.time.ZoneId.systemDefault()).toInstant()) == null
        ) return FocusPolicyState(null, null)
        if (studySession?.state == StudySessionState.ACTIVE && StudyTimerPolicy.finishIfDue(studySession, now.atZone(java.time.ZoneId.systemDefault()).toInstant()) == null) {
            return FocusPolicyState(
                blockedReason = BlockedReason.FOCUS_BLOCK,
                activeBlock = null,
                accessProfile = null,
                allowedPackageSnapshot = studySession.allowedPackages,
                allowedAppPackages = studySession.allowedAppPackages,
            )
        }
        return FocusPolicyState(blockedReason = null, activeBlock = null)
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
        state.allowedPackageSnapshot?.let { return allowed + it }

        if (state.blockedReason != BlockedReason.FOCUS_BLOCK) return allowed
        allowed += state.allowedAppPackages - CHROME_PACKAGE
        if (CHROME_PACKAGE in state.allowedAppPackages && siteFilterRunning) {
            allowed += CHROME_PACKAGE
        }
        return allowed
    }

    private const val CHROME_PACKAGE = "com.android.chrome"
}
