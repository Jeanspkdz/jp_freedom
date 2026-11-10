package com.jp.freedom.ui

import com.jp.freedom.domain.CompletionStatus

fun CompletionStatus.label(): String = when (this) {
    CompletionStatus.NOT_STARTED -> "Not started"
    CompletionStatus.FINISHED -> "Completed"
    CompletionStatus.PARTLY_DONE -> "Partially completed"
    CompletionStatus.NOT_DONE -> "Not completed"
}
