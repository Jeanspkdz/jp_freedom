package com.jp.freedom.domain

import java.time.LocalDate
import java.time.LocalTime

/** Legacy values retained only to decode version 1 snapshots and stored sessions. */
enum class AccessProfile(val title: String) {
    ESSENTIALS_ONLY("Essentials only"),
    ESSENTIALS_WITH_CHATGPT("Essentials and ChatGPT"),
    ESSENTIALS_WITH_CHATGPT_AND_SEARCH("Essentials, ChatGPT and Google Search"),
}

enum class CompletionStatus { NOT_STARTED, FINISHED, PARTLY_DONE, NOT_DONE }

data class PlannedBlock(
    val id: String,
    val date: LocalDate,
    val name: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val accessProfile: AccessProfile = AccessProfile.ESSENTIALS_ONLY,
    val completionStatus: CompletionStatus = CompletionStatus.NOT_STARTED,
    val note: String? = null,
)
