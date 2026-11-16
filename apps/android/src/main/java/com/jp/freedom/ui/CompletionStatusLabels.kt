package com.jp.freedom.ui

import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.SessionOutcome

fun CompletionStatus.label(): String = when (this) {
    CompletionStatus.NOT_STARTED -> "Sin iniciar"
    CompletionStatus.FINISHED -> "Finalizado"
    CompletionStatus.PARTLY_DONE -> "Parcial"
    CompletionStatus.NOT_DONE -> "No realizado"
}

fun SessionOutcome.label(): String = when (this) {
    SessionOutcome.FINISHED -> "Finalizado"
    SessionOutcome.NOT_FINISHED -> "No finalizado"
}
