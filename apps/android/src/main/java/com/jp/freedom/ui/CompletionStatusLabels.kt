package com.jp.freedom.ui

import com.jp.freedom.domain.CompletionStatus

fun CompletionStatus.label(): String = when (this) {
    CompletionStatus.NOT_STARTED -> "Aún no iniciado"
    CompletionStatus.FINISHED -> "Finalizado"
    CompletionStatus.PARTLY_DONE -> "Parcialmente hecho"
    CompletionStatus.NOT_DONE -> "No realizado"
}
