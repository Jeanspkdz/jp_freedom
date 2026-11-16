package com.jp.freedom.enforcement

/** Suppresses duplicate window events while still reacting to a different blocked app. */
internal class BlockScreenLaunchGate {
    private var lastBlockedPackage: String? = null
    private var lastLaunchAtMillis = Long.MIN_VALUE

    fun shouldLaunch(
        packageName: String,
        allowedPackages: Set<String>,
        nowMillis: Long,
    ): Boolean {
        if (packageName in allowedPackages) {
            reset()
            return false
        }

        if (packageName == lastBlockedPackage && nowMillis - lastLaunchAtMillis < DUPLICATE_EVENT_WINDOW_MILLIS) {
            return false
        }

        lastBlockedPackage = packageName
        lastLaunchAtMillis = nowMillis
        return true
    }

    fun reset() {
        lastBlockedPackage = null
        lastLaunchAtMillis = Long.MIN_VALUE
    }

    private companion object {
        const val DUPLICATE_EVENT_WINDOW_MILLIS = 750L
    }
}
