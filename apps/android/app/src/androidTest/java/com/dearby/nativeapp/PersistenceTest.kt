package com.dearby.nativeapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.dearby.nativeapp.entities.card.model.*
import com.dearby.nativeapp.features.guest.GuestStore
import com.dearby.nativeapp.shared.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    @Test fun roomReopenPreservesGuestAndPartialImport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "guest-${System.nanoTime()}.db"
        var database = Room.databaseBuilder(context, DearbyDatabase::class.java, name).build()
        val one = "00000000-0000-0000-0000-000000000001"; val two = "00000000-0000-0000-0000-000000000002"
        GuestStore(database.dao()).save(one, ExchangeContextModel(label = "Android"))
        GuestStore(database.dao()).save(two, ExchangeContextModel())
        database.close()
        database = Room.databaseBuilder(context, DearbyDatabase::class.java, name).build()
        val store = GuestStore(database.dao())
        assertEquals(setOf(one, two), store.all().map { it.cardId }.toSet())
        store.applyImport(setOf(one, two), listOf(ImportResultModel(one, "imported", "receipt"), ImportResultModel(two, "failed")))
        database.close()
        database = Room.databaseBuilder(context, DearbyDatabase::class.java, name).build()
        assertEquals(two, GuestStore(database.dao()).all().single().cardId)
        database.close(); context.deleteDatabase(name); Unit
    }
    @Test fun qrRejectsAmbiguousAndForeignPayloads() {
        val id = "00000000-0000-0000-0000-000000000001"
        val invalid = listOf("https://example.com/$id", "dearby://card/$id?label=a&label=b", "dearby://card/$id?unknown=x", "dearby://card/$id?activityId=$id&label=a", "dearby://card/$id#fragment", "dearby://user@card/$id", "dearby://card/1-1-1-1-1", "dearby://card/$id?label=" + "a".repeat(201))
        invalid.forEach { value -> assertTrue(value, runCatching { com.dearby.nativeapp.features.qr.QrActions.parse(value) }.isFailure) }
    }
    @Test fun qrPixelsDecodeAndKeystoreTokenSurvivesNewInstance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val link = com.dearby.nativeapp.features.qr.QrActions.link("00000000-0000-0000-0000-000000000001", ExchangeContextModel(label = "만남"))
        assertEquals(link, com.dearby.nativeapp.features.qr.QrActions.decode(com.dearby.nativeapp.features.qr.QrActions.bitmap(link)))
        val preferences = context.getSharedPreferences("session", 0)
        val previous = preferences.getString("ciphertext", null)
        try {
            TokenVault(context).write("instrumentation-only-token", "profile-test")
            assertEquals("instrumentation-only-token", TokenVault(context).read())
            assertEquals("profile-test", TokenVault(context).profileId())
            assertFalse(preferences.getString("ciphertext", "")!!.contains("instrumentation-only-token"))
        } finally { preferences.edit().apply { if (previous == null) remove("ciphertext") else putString("ciphertext", previous) }.commit() }
    }
}
