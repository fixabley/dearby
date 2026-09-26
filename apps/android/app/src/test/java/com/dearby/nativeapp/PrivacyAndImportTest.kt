package com.dearby.nativeapp

import com.dearby.nativeapp.entities.card.model.*
import com.dearby.nativeapp.features.guest.GuestStore
import com.dearby.nativeapp.shared.api.wireJson
import com.dearby.nativeapp.shared.storage.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class PrivacyAndImportTest {
    @Test fun publicationContainsOnlyExplicitIds() {
        val selection = CardSelectionModel("개발자", "소개", setOf("email"), emptySet()).validated(setOf("email", "private-phone"), setOf("private-history"))
        val json = wireJson.encodeToString(selection)
        assertTrue(json.contains("email"))
        assertFalse(json.contains("private-phone"))
        assertFalse(json.contains("private-history"))
        assertFalse(json.contains("contacts"))
    }
    @Test(expected = IllegalArgumentException::class) fun staleSelectionCannotPublish() { CardSelectionModel("명함", "", setOf("removed"), emptySet()).validated(emptySet(), emptySet()) }
    @Test fun noSelectionIsPermittedWithoutPublishingHiddenFields() { assertEquals(emptySet<String>(), CardSelectionModel("명함", "", emptySet(), emptySet()).validated(setOf("private"), setOf("private")).contactIds) }
    @Test fun partialImportOnlyDeletesConfirmedSelectedIds() = runTest {
        val dao = MemoryDao(); val store = GuestStore(dao)
        val ids = (1..4).map { "00000000-0000-0000-0000-00000000000$it" }
        ids.forEach { store.save(it, ExchangeContextModel(label = "컨퍼런스")) }
        store.applyImport(ids.take(3).toSet(), listOf(ImportResultModel(ids[0], "imported", "r1"), ImportResultModel(ids[1], "failed"), ImportResultModel(ids[2], "alreadySaved", "r3"), ImportResultModel(ids[3], "imported", "unrequested")))
        assertEquals(setOf(ids[1], ids[3]), store.all().map { it.cardId }.toSet())
    }
    @Test fun duplicateGuestPreservesOriginalIdContextAndTime() = runTest {
        val store = GuestStore(MemoryDao()); val id = "00000000-0000-0000-0000-000000000001"
        store.save(id, ExchangeContextModel(label = "첫 활동")); val original = store.all().single()
        store.save(id, ExchangeContextModel(label = "다른 활동"))
        assertEquals(original, store.all().single())
    }
    @Test fun missingMalformedAndConflictingImportResponsesPreserveIds() {
        assertTrue(importedIds(setOf("a", "b", "c"), listOf(ImportResultModel("a", "imported"), ImportResultModel("b", "unknown", "r"), ImportResultModel("c", "imported", "r"), ImportResultModel("c", "failed"))).isEmpty())
        assertTrue(importedIds(setOf("a"), emptyList()).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun exchangeContextCannotCombineRegisteredAndFreeText() { ExchangeContextModel("id", "label") }
}
internal class MemoryDao : DearbyDao {
    val documents = mutableMapOf<String, DocumentRecord>()
    private val guests = linkedMapOf<String, GuestRecord>()
    override suspend fun document(key: String) = documents[key]
    override suspend fun put(record: DocumentRecord) { documents[record.key] = record }
    override suspend fun removeDocument(key: String) { documents.remove(key) }
    override suspend fun clearAccount() { documents.keys.removeAll { it.startsWith("account:") } }
    override suspend fun guests() = guests.values.toList()
    override suspend fun saveGuest(record: GuestRecord) { guests.putIfAbsent(record.cardId, record) }
    override suspend fun removeGuests(ids: Set<String>) { ids.forEach(guests::remove) }
}
