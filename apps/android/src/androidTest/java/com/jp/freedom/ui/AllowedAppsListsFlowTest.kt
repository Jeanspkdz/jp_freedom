package com.jp.freedom.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jp.freedom.data.AllowedAppsCatalog
import com.jp.freedom.domain.InstalledApp
import com.jp.freedom.domain.AllowedAppsList
import com.jp.freedom.domain.AllowedAppsListPolicy
import com.jp.freedom.domain.AllowedAppsListValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllowedAppsListsFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun appNameMatchesTakePriorityOverPackageNameMatches() {
        compose.setContent {
            FocusTheme {
                AllowedAppsListEditorScreen(
                    existingList = null,
                    lists = emptyList(),
                    installedApps = listOf(
                        InstalledApp("com.google.android.googlequicksearchbox", "Google"),
                        InstalledApp("com.google.android.calendar", "Calendar"),
                    ),
                    locked = false,
                    onBack = {},
                    onSave = { AllowedAppsListValidation.Valid },
                )
            }
        }

        compose.onNodeWithText("Search apps").performTextInput("Google")

        compose.onNodeWithText("com.google.android.googlequicksearchbox").assertIsDisplayed()
        compose.onAllNodesWithText("com.google.android.calendar").assertCountEquals(0)
    }

    @Test
    fun genericSearchDoesNotMatchOnlyThePackageNamespace() {
        compose.setContent {
            FocusTheme {
                AllowedAppsListEditorScreen(
                    existingList = null,
                    lists = emptyList(),
                    installedApps = listOf(InstalledApp("com.google.android.calendar", "Calendar")),
                    locked = false,
                    onBack = {},
                    onSave = { AllowedAppsListValidation.Valid },
                )
            }
        }

        compose.onNodeWithText("Search apps").performTextInput("Google")

        compose.onNodeWithText("Calendar").assertDoesNotExist()
        compose.onNodeWithText("No installed apps found.").assertIsDisplayed()
    }

    @Test
    fun installedAnkiAppearsInNewList() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val ankiPackage = "com.ichi2.anki"
        assumeTrue("Anki is installed on this device", context.packageManager.getLaunchIntentForPackage(ankiPackage) != null)
        val installedApps = AllowedAppsCatalog.installedLaunchableApps(context)
        compose.setContent {
            FocusTheme {
                AllowedAppsListEditorScreen(
                    existingList = null,
                    lists = emptyList(),
                    installedApps = installedApps,
                    locked = false,
                    onBack = {},
                    onSave = { AllowedAppsListValidation.Valid },
                )
            }
        }

        compose.onNodeWithText("Search apps").performTextInput(ankiPackage)
        compose.onNodeWithText("No installed apps found.").assertDoesNotExist()
    }

    @Test
    fun createListButtonStaysAboveSystemNavigation() {
        var navigationInsetDp = 0f
        compose.setContent {
            val density = LocalDensity.current
            navigationInsetDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp().value }
            FocusTheme {
                AllowedAppsListsScreen(
                    lists = emptyList(),
                    installedApps = emptyList(),
                    selectedListId = AllowedAppsList.ESSENTIALS_ID,
                    selectionMode = true,
                    lockedListId = null,
                    onBack = {}, onSelect = {}, onCreate = {}, onEdit = {}, onDelete = {},
                )
            }
        }

        compose.onNodeWithText("Create list").assertIsDisplayed()
        val rootBottom = compose.onRoot().getUnclippedBoundsInRoot().bottom.value
        val buttonBottom = compose.onNodeWithText("Create list").getUnclippedBoundsInRoot().bottom.value
        assertTrue("The button must clear the navigation bar", buttonBottom <= rootBottom - navigationInsetDp)
    }

    @Test
    fun newListCanBeNamedAndSavedWhenNoTimerIsActive() {
        var savedList: AllowedAppsList? = null
        compose.setContent {
            FocusTheme {
                AllowedAppsListEditorScreen(
                    existingList = null,
                    lists = emptyList(),
                    installedApps = listOf(InstalledApp("com.example.calendar", "Calendar")),
                    locked = AllowedAppsListPolicy.isEditorLocked(editorListId = null, activeSessionListId = null),
                    onBack = {},
                    onSave = {
                        savedList = it
                        AllowedAppsListValidation.Valid
                    },
                )
            }
        }

        compose.onNodeWithText("List name").performTextInput("Reading")
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("Save list").assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertEquals("Reading", savedList?.name)
            assertEquals(setOf("com.example.calendar"), savedList?.packageNames)
        }
    }

    @Test
    fun listsCanBeSelectedForANewTimerAndCreatedBeforeItStarts() {
        var selectedId: String? = null
        var createRequested = false
        val readingList = AllowedAppsList("reading", "Reading", setOf("com.example.calendar"))
        compose.setContent {
            FocusTheme {
                AllowedAppsListsScreen(
                    lists = listOf(readingList),
                    installedApps = listOf(InstalledApp("com.example.calendar", "Calendar")),
                    selectedListId = AllowedAppsList.ESSENTIALS_ID,
                    selectionMode = true,
                    lockedListId = null,
                    onBack = {},
                    onSelect = { selectedId = it.id },
                    onCreate = { createRequested = true },
                    onEdit = {},
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithText("Reading").performScrollTo().performClick()
        compose.onNodeWithText("Create list").performClick()

        compose.runOnIdle {
            assertEquals("reading", selectedId)
            assertTrue(createRequested)
        }
    }

    @Test
    fun listInAnActiveSessionCannotBeEdited() {
        val readingList = AllowedAppsList("reading", "Reading", setOf("com.example.calendar"))
        compose.setContent {
            FocusTheme {
                AllowedAppsListEditorScreen(
                    existingList = readingList,
                    lists = listOf(readingList),
                    installedApps = listOf(InstalledApp("com.example.calendar", "Calendar")),
                    locked = AllowedAppsListPolicy.isEditorLocked(
                        editorListId = readingList.id,
                        activeSessionListId = readingList.id,
                    ),
                    onBack = {},
                    onSave = { AllowedAppsListValidation.Valid },
                )
            }
        }

        compose.onNodeWithText("This list is locked while its session is active.").assertIsDisplayed()
        compose.onNodeWithText("List name").assertIsNotEnabled()
    }
}
