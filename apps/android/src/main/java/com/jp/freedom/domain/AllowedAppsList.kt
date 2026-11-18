package com.jp.freedom.domain

data class AllowedAppsList(
    val id: String,
    val name: String,
    val packageNames: Set<String>,
) {
    companion object {
        const val ESSENTIALS_ID = "essentials"
        val ESSENTIALS = AllowedAppsList(ESSENTIALS_ID, "Essentials", emptySet())

        fun fromLegacyAccessProfile(value: String): AllowedAppsList = when (value) {
            "ESSENTIALS_WITH_CHATGPT" -> AllowedAppsList(
                "legacy-essentials-with-chatgpt",
                "Essentials and ChatGPT",
                setOf("com.openai.chatgpt"),
            )
            "ESSENTIALS_WITH_CHATGPT_AND_SEARCH" -> AllowedAppsList(
                "legacy-essentials-with-chatgpt-and-search",
                "Essentials, ChatGPT and Google Search",
                setOf("com.openai.chatgpt", "com.google.android.googlequicksearchbox"),
            )
            else -> ESSENTIALS
        }
    }
}

data class InstalledApp(
    val packageName: String,
    val label: String,
)

sealed interface AllowedAppsListValidation {
    data object Valid : AllowedAppsListValidation
    data object ActiveSessionLocked : AllowedAppsListValidation
    data object MissingName : AllowedAppsListValidation
    data object NameTooLong : AllowedAppsListValidation
    data object DuplicateName : AllowedAppsListValidation
}

object AllowedAppsListPolicy {
    const val MAX_NAME_LENGTH = 40

    fun isEditorLocked(editorListId: String?, activeSessionListId: String?): Boolean =
        editorListId != null && editorListId == activeSessionListId

    fun validate(
        name: String,
        lists: List<AllowedAppsList>,
        excludingId: String? = null,
    ): AllowedAppsListValidation {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return AllowedAppsListValidation.MissingName
        if (normalizedName.length > MAX_NAME_LENGTH) return AllowedAppsListValidation.NameTooLong
        if (normalizedName.equals(AllowedAppsList.ESSENTIALS.name, ignoreCase = true) ||
            lists.any { it.id != excludingId && it.name.equals(normalizedName, ignoreCase = true) }
        ) {
            return AllowedAppsListValidation.DuplicateName
        }
        return AllowedAppsListValidation.Valid
    }
}
