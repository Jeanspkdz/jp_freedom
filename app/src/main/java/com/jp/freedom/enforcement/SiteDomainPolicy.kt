package com.jp.freedom.enforcement

import com.jp.freedom.domain.AccessProfile

object SiteDomainPolicy {
    private val chatGptDomains = setOf("chatgpt.com", "openai.com", "oaistatic.com", "oaiusercontent.com")
    private val searchHosts = setOf("google.com", "www.google.com", "www.gstatic.com", "encrypted-tbn0.gstatic.com")

    fun isAllowed(domain: String, state: FocusPolicyState): Boolean {
        if (state.blockedReason == null) return true
        if (state.blockedReason == FocusPolicy.EVENING_RESTRICTION) return false

        val profile = state.activeBlock?.accessProfile ?: return false
        val normalized = domain.trimEnd('.').lowercase()
        val chatGptAllowed = profile != AccessProfile.ESSENTIALS_ONLY
        val searchAllowed = profile == AccessProfile.ESSENTIALS_WITH_CHATGPT_AND_SEARCH
        if (chatGptAllowed && matchesDomain(normalized, chatGptDomains)) return true
        return searchAllowed && normalized in searchHosts
    }

    private fun matchesDomain(domain: String, suffixes: Set<String>): Boolean =
        suffixes.any { domain == it || domain.endsWith(".$it") }
}
