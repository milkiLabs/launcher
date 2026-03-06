package com.milki.launcher.data.repository

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for [FolderMutationEngine].
 *
 * WHY THESE TESTS EXIST:
 * Folder-domain logic moved out of `HomeRepositoryImpl` into this dedicated engine.
 * These tests lock expected behavior for the core mutation paths requested in the
 * folders audit: create, add, merge, extract, and move.
 *
 * SCOPE:
 * - In-memory mutation semantics only (pure list transformations).
 * - No DataStore, no Android framework, and no threading behavior.
 * - Focused on invariant correctness and shape of resulting list state.
 */
class FolderMutationEngineContractTest {

    private val engine = FolderMutationEngine()

    /**
     * CREATE PATH:
     * Two top-level icon items become one new folder with both children.
     */
    @Test
    fun create_folder_replaces_source_items_with_single_folder() {
        val itemA = app(id = "app:a", label = "A", position = GridPosition(0, 0))
        val itemB = app(id = "app:b", label = "B", position = GridPosition(0, 1))
        val items = mutableListOf<HomeItem>(itemA, itemB)

        val created = engine.createFolder(
            items = items,
            item1 = itemA,
            item2 = itemB,
            atPosition = GridPosition(1, 2)
        )

        assertNotNull(created)
        assertEquals(1, items.size)

        val onlyItem = items.single() as HomeItem.FolderItem
        assertEquals(created?.id, onlyItem.id)
        assertEquals(GridPosition(1, 2), onlyItem.position)
        assertEquals(listOf("app:a", "app:b"), onlyItem.children.map { it.id })
        assertEquals(GridPosition.DEFAULT, onlyItem.children[0].position)
        assertEquals(GridPosition.DEFAULT, onlyItem.children[1].position)
    }

    /**
     * ADD PATH:
     * Item is moved into target folder at requested index and removed from top-level.
     */
    @Test
    fun add_item_to_folder_moves_item_from_top_level_and_inserts_at_target_index() {
        val childA = app(id = "app:childA", label = "ChildA", position = GridPosition.DEFAULT)
        val childB = app(id = "app:childB", label = "ChildB", position = GridPosition.DEFAULT)
        val targetFolder = HomeItem.FolderItem(
            id = "folder:target",
            children = listOf(childA, childB),
            position = GridPosition(0, 0)
        )
        val itemToAdd = app(id = "app:new", label = "New", position = GridPosition(4, 4))

        val items = mutableListOf<HomeItem>(targetFolder, itemToAdd)

        val applied = engine.addItemToFolder(
            items = items,
            folderId = targetFolder.id,
            item = itemToAdd,
            targetIndex = 1
        )

        assertTrue(applied)
        assertEquals(1, items.size)

        val updatedFolder = items.single() as HomeItem.FolderItem
        assertEquals(listOf("app:childA", "app:new", "app:childB"), updatedFolder.children.map { it.id })
        assertEquals(GridPosition.DEFAULT, updatedFolder.children[1].position)
    }

    /**
     * MERGE PATH:
     * Source folder is removed and only unique source children are appended to target.
     */
    @Test
    fun merge_folders_appends_unique_children_and_deletes_source_folder() {
        val shared = app(id = "app:shared", label = "Shared", position = GridPosition.DEFAULT)
        val sourceOnly = app(id = "app:source-only", label = "SourceOnly", position = GridPosition.DEFAULT)
        val targetOnly = app(id = "app:target-only", label = "TargetOnly", position = GridPosition.DEFAULT)

        val source = HomeItem.FolderItem(
            id = "folder:source",
            children = listOf(shared, sourceOnly),
            position = GridPosition(2, 0)
        )
        val target = HomeItem.FolderItem(
            id = "folder:target",
            children = listOf(shared, targetOnly),
            position = GridPosition(2, 1)
        )
        val items = mutableListOf<HomeItem>(source, target)

        val applied = engine.mergeFolders(
            items = items,
            sourceFolderId = source.id,
            targetFolderId = target.id
        )

        assertTrue(applied)
        assertEquals(1, items.size)

        val updatedTarget = items.single() as HomeItem.FolderItem
        assertEquals(target.id, updatedTarget.id)
        assertEquals(
            listOf("app:shared", "app:target-only", "app:source-only"),
            updatedTarget.children.map { it.id }
        )
    }

    /**
     * EXTRACT PATH:
     * Folder child moves to home grid target when target is unoccupied.
     */
    @Test
    fun extract_item_from_folder_places_child_on_target_cell() {
        val moving = app(id = "app:moving", label = "Moving", position = GridPosition.DEFAULT)
        val survivorA = app(id = "app:survivorA", label = "SurvivorA", position = GridPosition.DEFAULT)
        val survivorB = app(id = "app:survivorB", label = "SurvivorB", position = GridPosition.DEFAULT)
        val sourceFolder = HomeItem.FolderItem(
            id = "folder:source",
            children = listOf(moving, survivorA, survivorB),
            position = GridPosition(0, 0)
        )

        val items = mutableListOf<HomeItem>(sourceFolder)

        val applied = engine.extractItemFromFolder(
            items = items,
            folderId = sourceFolder.id,
            itemId = moving.id,
            targetPosition = GridPosition(5, 1),
            targetPositionOccupiedByOtherItem = false
        )

        assertTrue(applied)
        assertEquals(2, items.size)

        val updatedFolder = items.first { it.id == sourceFolder.id } as HomeItem.FolderItem
        assertEquals(listOf("app:survivorA", "app:survivorB"), updatedFolder.children.map { it.id })

        val extracted = items.firstOrNull { it.id == moving.id }
        assertNotNull(extracted)
        assertEquals(GridPosition(5, 1), extracted?.position)
    }

    /**
     * MOVE PATH:
     * Child moves from source folder to target folder and source cleanup policy is applied.
     */
    @Test
    fun move_item_between_folders_moves_child_and_unwraps_source_when_one_child_remains() {
        val moving = app(id = "app:moving", label = "Moving", position = GridPosition.DEFAULT)
        val sourceRemaining = app(id = "app:source-remaining", label = "SourceRemaining", position = GridPosition.DEFAULT)
        val targetChild = app(id = "app:target-child", label = "TargetChild", position = GridPosition.DEFAULT)

        val sourceFolder = HomeItem.FolderItem(
            id = "folder:source",
            children = listOf(moving, sourceRemaining),
            position = GridPosition(3, 2)
        )
        val targetFolder = HomeItem.FolderItem(
            id = "folder:target",
            children = listOf(targetChild),
            position = GridPosition(0, 1)
        )
        val items = mutableListOf<HomeItem>(sourceFolder, targetFolder)

        val applied = engine.moveItemBetweenFolders(
            items = items,
            sourceFolderId = sourceFolder.id,
            targetFolderId = targetFolder.id,
            itemId = moving.id
        )

        assertTrue(applied)
        assertEquals(2, items.size)

        // Source folder should be unwrapped because one child remained after removal.
        assertNull(items.firstOrNull { it.id == sourceFolder.id })
        val promoted = items.firstOrNull { it.id == sourceRemaining.id }
        assertNotNull(promoted)
        assertEquals(sourceFolder.position, promoted?.position)

        val updatedTarget = items.first { it.id == targetFolder.id } as HomeItem.FolderItem
        assertEquals(listOf("app:target-child", "app:moving"), updatedTarget.children.map { it.id })
        assertEquals(GridPosition.DEFAULT, updatedTarget.children.last().position)
    }

    /**
     * Simple pinned-app fixture helper used by all contract tests.
     */
    private fun app(id: String, label: String, position: GridPosition): HomeItem.PinnedApp {
        return HomeItem.PinnedApp(
            id = id,
            packageName = "com.example.${id.replace(':', '_')}",
            activityName = "MainActivity",
            label = label,
            position = position
        )
    }

    /**
     * Negative guard sample to keep rejection semantics locked as well.
     */
    @Test
    fun extract_rejects_when_target_cell_is_occupied() {
        val moving = app(id = "app:moving", label = "Moving", position = GridPosition.DEFAULT)
        val sourceFolder = HomeItem.FolderItem(
            id = "folder:source",
            children = listOf(moving, app("app:other", "Other", GridPosition.DEFAULT)),
            position = GridPosition(1, 1)
        )
        val items = mutableListOf<HomeItem>(sourceFolder)

        val applied = engine.extractItemFromFolder(
            items = items,
            folderId = sourceFolder.id,
            itemId = moving.id,
            targetPosition = GridPosition(2, 2),
            targetPositionOccupiedByOtherItem = true
        )

        assertFalse(applied)
        val unchangedFolder = items.single() as HomeItem.FolderItem
        assertEquals(listOf("app:moving", "app:other"), unchangedFolder.children.map { it.id })
    }
}
