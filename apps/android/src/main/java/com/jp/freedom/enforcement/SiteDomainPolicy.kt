package com.jp.freedom.enforcement

import com.jp.freedom.domain.AccessProfile

object SiteDomainPolicy {
    private val chatGptDomains = setOf(
        "chatgpt.com",
        "chat.openai.com",
        "oaistatic.com",
        "oaiusercontent.com",
    )
    private val chatGptAuthDomains = setOf("auth.openai.com")
    private val searchHosts = setOf("google.com", "www.google.com", "www.gstatic.com", "encrypted-tbn0.gstatic.com")

    fun isAllowed(domain: String, state: FocusPolicyState): Boolean {
        if (state.blockedReason == BlockedReason.EVENING_RESTRICTION) return false
        if (state.blockedReason == null && state.activeBlock == null) return true

        val normalized = domain.trimEnd('.').lowercase()
        if (state.allowedPackageSnapshot != null) {
            if ("com.android.chrome" !in state.allowedPackageSnapshot) return false
            return matchesDomain(normalized, chatGptDomains) || normalized in chatGptAuthDomains || normalized in searchHosts
        }

        val chatGptAllowed = CHATGPT_PACKAGE in state.allowedAppPackages ||
            (state.accessProfile != null && state.accessProfile != AccessProfile.ESSENTIALS_ONLY)
        val searchAllowed = GOOGLE_SEARCH_PACKAGE in state.allowedAppPackages ||
            state.accessProfile == AccessProfile.ESSENTIALS_WITH_CHATGPT_AND_SEARCH
        if (chatGptAllowed &&
            (matchesDomain(normalized, chatGptDomains) || normalized in chatGptAuthDomains)
        ) return true
        return searchAllowed && normalized in searchHosts
    }

    private fun matchesDomain(domain: String, suffixes: Set<String>): Boolean =
        suffixes.any { domain == it || domain.endsWith(".$it") }

    private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    private const val GOOGLE_SEARCH_PACKAGE = "com.google.android.googlequicksearchbox"
}
