package io.fixabley.dearby.core.state

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import io.fixabley.dearby.core.data.FavoriteStore
import org.junit.Assert.*
import org.junit.Test

class FavoritesStateTest {
    @Test
    fun restoresExistingIdsWithoutReplacingLegacyOrganizations() {
        val store = MemoryStore(setOf("cbnu-career", "unknown-legacy-id"))
        val state = FavoritesState(store)
        assertEquals(setOf("cbnu-career", "unknown-legacy-id"), state.ids)
        state.save("krc")
        assertEquals(setOf("cbnu-career", "unknown-legacy-id", "krc"), FavoritesState(store).ids)
    }

    @Test
    fun addingIsIdempotentAndDeletingIsExplicitAndPersistent() {
        val store = MemoryStore()
        val state = FavoritesState(store)
        state.save("krc")
        state.save("krc")
        state.save("db-insurance")
        assertEquals(setOf("krc", "db-insurance"), state.ids)
        assertEquals(state.ids, FavoritesState(store).ids)
        state.remove("krc")
        state.remove("absent")
        assertEquals(setOf("db-insurance"), FavoritesState(store).ids)
        state.remove("db-insurance")
        assertTrue(FavoritesState(store).ids.isEmpty())
    }

    @Test
    fun savingProgramDoesNotAddItsParentOrEventSchool() {
        val state = FavoritesState(MemoryStore())
        state.save("yeongnam-cyber-defense")
        state.save("krc")
        assertEquals(setOf("yeongnam-cyber-defense", "krc"), state.ids)
    }

    @Test
    fun bothConsumersObserveTheSameAddAndRemoveWithoutAnApp() {
        val state = FavoritesState(MemoryStore())
        val observer = SnapshotStateObserver { it() }
        val observed = mutableMapOf<String, Set<String>>()
        val invalidated = mutableSetOf<String>()
        val onChanged: (String) -> Unit = { invalidated.add(it) }
        fun observeConsumers() {
            for (consumer in listOf("discovery", "favorites")) {
                observer.observeReads(consumer, onChanged) { observed[consumer] = state.ids }
            }
        }
        observer.start()
        try {
            Snapshot.sendApplyNotifications()
            observeConsumers()
            state.save("krc")
            Snapshot.sendApplyNotifications()
            assertEquals(setOf("discovery", "favorites"), invalidated)
            observeConsumers()
            assertEquals(setOf("krc"), observed["discovery"])
            assertEquals(observed["discovery"], observed["favorites"])
            invalidated.clear()
            state.remove("krc")
            Snapshot.sendApplyNotifications()
            assertEquals(setOf("discovery", "favorites"), invalidated)
            observeConsumers()
            assertEquals(emptySet<String>(), observed["discovery"])
            assertEquals(observed["discovery"], observed["favorites"])
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test
    fun takesSnapshotOfStoreDataInsteadOfSharingMutableReadSet() {
        val original = mutableSetOf("krc", "db-insurance")
        val store = object : FavoriteStore {
            override fun read(): Set<String> = original
            override fun write(ids: Set<String>) = Unit
        }
        val state = FavoritesState(store)
        original.clear()
        assertEquals(setOf("krc", "db-insurance"), state.ids)
    }

    private class MemoryStore(initial: Set<String> = emptySet()) : FavoriteStore {
        private var saved = initial.toSet()
        override fun read(): Set<String> = saved.toSet()
        override fun write(ids: Set<String>) { saved = ids.toSet() }
    }
}
