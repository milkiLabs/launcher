package com.milki.launcher.ui.screens

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.ui.screens.launcher.FolderActions
import com.milki.launcher.ui.screens.launcher.HomeActions
import com.milki.launcher.ui.screens.launcher.LauncherActions
import com.milki.launcher.ui.screens.launcher.SearchActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the grouped LauncherScreen action model.
 *
 * WHY THESE TESTS EXIST:
 * The LauncherScreen action model groups callbacks by feature domain. These
 * tests lock down the contract expected by the launcher host so future refactors
 * can safely evolve internals without breaking routing behavior.
 *
 * IMPORTANT SCOPE:
 * - These are unit-level contract tests for callback grouping and routing.
 * - They intentionally do NOT test Compose rendering.
 * - They focus on boundary guarantees: defaults are safe, groups are independent,
 *   and launcher triggers reach the expected callback unchanged.
 */
class LauncherActionsContractTest {

    /**
     * Regression guard: default contracts must be no-ops and safe to call.
     *
     * Rationale:
     * LauncherScreen uses default action objects in some tooling paths and during
     * incremental integration work. If defaults ever stop being safe no-ops,
     * preview/test scaffolding can crash even before business callbacks are wired.
     */
    @Test
    fun default_actions_are_safe_noops() {
        val actions = LauncherActions()

        val pinnedApp = samplePinnedApp(id = "app:demo/noop")
        val target = GridPosition(row = 1, column = 2)

        // All invocations below should complete without throwing exceptions.
        actions.home.onPinnedItemClick(pinnedApp)
        actions.home.onPinnedItemLongPress(pinnedApp)
        actions.home.onPinnedItemMove(pinnedApp.id, target)
        actions.home.onItemDroppedToHome(pinnedApp, target)
        actions.home.onHomeTrigger(LauncherTrigger.HOME_TAP)
        actions.home.onHomeTrigger(LauncherTrigger.HOME_SWIPE_UP)
        actions.home.onHomeTrigger(LauncherTrigger.HOME_SWIPE_DOWN)

        actions.folder.onCreateFolder(pinnedApp, pinnedApp, target)
        actions.folder.onAddItemToFolder("folder-a", pinnedApp)
        actions.folder.onMergeFolders("folder-a", "folder-b")
        actions.folder.onFolderClose()
        actions.folder.onFolderRename("folder-a", "Renamed")
        actions.folder.onFolderItemClick(pinnedApp)
        actions.folder.onFolderItemRemove("folder-a", pinnedApp.id)
        actions.folder.onFolderItemReorder("folder-a", listOf(pinnedApp))
        actions.folder.onExtractItemFromFolder("folder-a", pinnedApp.id, target)
        actions.folder.onMoveFolderItemToFolder("folder-a", pinnedApp.id, "folder-b")
        actions.folder.onFolderChildDroppedOnItem("folder-a", pinnedApp, pinnedApp, target)

        actions.widget.onWidgetPickerOpenChange(true)
        actions.widget.onRemoveWidget("widget-1", 101)
        actions.widget.onUpdateWidgetFrame("widget-1", target, sampleWidgetSpan())

        actions.drawer.onAppDrawerOpenChange(true)

        actions.search.onQueryChange("calculator")
        actions.search.onDismissSearch()

        actions.menu.onOpenSettings()
        actions.menu.onHomescreenMenuOpenChange(true)
    }

    /**
     * Contract: the home group should receive exact payloads passed from the caller.
     *
     * Rationale:
     * This verifies the grouped model preserves callback semantics while using a
     * scalable trigger-based home gesture API.
     */
    @Test
    fun home_group_forwards_trigger_and_payloads_without_mutation() {
        var capturedId = ""
        var capturedPosition = GridPosition.DEFAULT
        val triggered = mutableListOf<LauncherTrigger>()

        val actions = LauncherActions(
            home = HomeActions(
                onPinnedItemMove = { itemId, newPosition ->
                    capturedId = itemId
                    capturedPosition = newPosition
                },
                onHomeTrigger = { trigger ->
                    triggered += trigger
                }
            )
        )

        val expectedId = "app:com.test/.Main"
        val expectedPosition = GridPosition(row = 3, column = 1)

        actions.home.onPinnedItemMove(expectedId, expectedPosition)
        actions.home.onHomeTrigger(LauncherTrigger.HOME_TAP)
        actions.home.onHomeTrigger(LauncherTrigger.HOME_SWIPE_UP)
        actions.home.onHomeTrigger(LauncherTrigger.HOME_SWIPE_DOWN)

        assertEquals(expectedId, capturedId)
        assertEquals(expectedPosition, capturedPosition)
        assertEquals(
            listOf(
                LauncherTrigger.HOME_TAP,
                LauncherTrigger.HOME_SWIPE_UP,
                LauncherTrigger.HOME_SWIPE_DOWN
            ),
            triggered
        )
    }

    /**
     * Contract: feature groups are isolated from each other.
     *
     * Rationale:
     * We explicitly validate that invoking one feature domain does not implicitly
     * trigger callbacks in a different domain. This protects against accidental
     * cross-wiring when constructing LauncherActions in the launcher host.
     */
    @Test
    fun feature_groups_remain_isolated() {
        var folderCloseCalled = false
        var searchDismissCalled = false

        val actions = LauncherActions(
            folder = FolderActions(
                onFolderClose = {
                    folderCloseCalled = true
                }
            ),
            search = SearchActions(
                onDismissSearch = {
                    searchDismissCalled = true
                }
            )
        )

        actions.folder.onFolderClose()

        assertTrue(folderCloseCalled)
        assertFalse(searchDismissCalled)
    }

    private fun samplePinnedApp(id: String): HomeItem.PinnedApp {
        return HomeItem.PinnedApp(
            id = id,
            packageName = "com.example.app",
            activityName = "MainActivity",
            label = "Example",
            position = GridPosition(row = 0, column = 0)
        )
    }

    private fun sampleWidgetSpan(): GridSpan {
        return GridSpan(columns = 2, rows = 1)
    }
}
