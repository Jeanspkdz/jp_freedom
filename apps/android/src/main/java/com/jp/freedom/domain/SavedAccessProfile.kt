package com.jp.freedom.domain

data class SavedAccessProfile(
    val id: String,
    val name: String,
    val packages: Set<String>,
)
