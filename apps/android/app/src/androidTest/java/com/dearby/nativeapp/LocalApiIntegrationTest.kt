package com.dearby.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.dearby.nativeapp.entities.card.api.CardRepository
import com.dearby.nativeapp.entities.card.model.*
import com.dearby.nativeapp.entities.profile.api.ProfileRepository
import com.dearby.nativeapp.entities.profile.model.*
import com.dearby.nativeapp.features.account.AuthRepository
import com.dearby.nativeapp.features.guest.GuestStore
import com.dearby.nativeapp.features.wallet.WalletRepository
import com.dearby.nativeapp.features.wallet.SendRequest
import com.dearby.nativeapp.features.wallet.DeliveryModel
import kotlinx.serialization.encodeToString
import com.dearby.nativeapp.shared.api.*
import com.dearby.nativeapp.shared.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Opt-in actual isolated backend. OTP is injected into private app storage by the host, never logged. */
class LocalApiIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    @Test fun requestChallenge() = runBlocking {
        assumeTrue(args.getString("localApi") == "true")
        val challenge = AuthRepository(HttpClient(BuildConfig.API_BASE_URL, BuildConfig.DEBUG) { null }).challenge("android@example.test")
        File(context.filesDir, "integration-challenge").writeText(challenge.challengeId)
    }
    @Test fun authenticatedProfilePublicationAndImport() = runBlocking {
        assumeTrue(args.getString("localApi") == "true")
        val vault = TokenVault(context)
        val http = HttpClient(BuildConfig.API_BASE_URL, BuildConfig.DEBUG, vault::read)
        val otpFile = File(context.filesDir, "integration-otp")
        val session = AuthRepository(http).login(File(context.filesDir, "integration-challenge").readText(), otpFile.readText().trim())
        otpFile.delete()
        vault.write(session.sessionToken, session.profileId)
        val database = Room.databaseBuilder(context, DearbyDatabase::class.java, "dearby.db").build()
        try {
            val profiles = ProfileRepository(http, database.dao()).apply { accountId = session.profileId }
            val contactId = UUID.randomUUID().toString(); val hiddenId = UUID.randomUUID().toString()
            val current = profiles.profile()
            val profile = profiles.saveProfile(current.copy(name = "Android 검증 계정", job = "Android 개발자", introduction = "실제 로컬 API 통합 검증", contacts = listOf(ContactModel(contactId, "email", "이메일", "android@example.test"), ContactModel(hiddenId, "phone", "비공개 전화", "01000000000"))))
            assertEquals("Android 검증 계정", profile.name)
            val cards = CardRepository(http, database.dao())
            val card = cards.publish(CardSelectionModel("컨퍼런스 명함", "로컬 서버에 발행한 명함", setOf(contactId), emptySet()))
            val public = cards.card(card.id)
            assertEquals(listOf(contactId), public.contacts.map { it.id }); assertFalse(public.contacts.any { it.id == hiddenId })
            File(context.filesDir, "integration-public-ids").writeText("${session.profileId}\n${card.id}")
            val store = GuestStore(database.dao()); store.save(card.id, ExchangeContextModel(label = "통합 검증"))
            val local = store.all().filter { it.cardId == card.id }
            val results = WalletRepository(http, database.dao()).import(local)
            store.applyImport(setOf(card.id), results)
            assertFalse(store.all().any { it.cardId == card.id })
            assertTrue(WalletRepository(http, database.dao()).wallet().any { it.card.id == card.id })
        } finally { database.close() }
    }
    @Test fun deliverToOtherPlatformAndVerifyServerWallet() = runBlocking {
        assumeTrue(args.getString("localApi") == "true" && args.getString("recipientId") != null)
        val vault = TokenVault(context); val http = HttpClient(BuildConfig.API_BASE_URL, BuildConfig.DEBUG, vault::read)
        val db = Room.databaseBuilder(context, DearbyDatabase::class.java, "dearby.db").build()
        try {
            val ids = File(context.filesDir, "integration-public-ids").readLines()
            val wallet = WalletRepository(http, db.dao())
            val recipient = args.getString("recipientId")!!
            val exchange = ExchangeContextModel(label = "cross-platform test")
            val request = SendRequest(ids[1], recipient, exchange, UUID.randomUUID().toString())
            db.dao().put(DocumentRecord("account:${ids[0]}:pending-send", wireJson.encodeToString(request)))
            val delivery = wallet.send(ids[1], recipient, exchange, ids[0])
            val replay = wireJson.decodeFromString<DeliveryModel>(http.request("POST", "/exchanges", wireJson.encodeToString(request)))
            assertEquals(delivery.receiptId, replay.receiptId)
            assertTrue(delivery.receiptId.isNotBlank())
        } finally { db.close() }
    }
    @Test fun reciprocalAfterOtherPlatformSends() = runBlocking {
        assumeTrue(args.getString("localApi") == "true" && args.getString("recipientId") != null)
        val vault = TokenVault(context); val http = HttpClient(BuildConfig.API_BASE_URL, BuildConfig.DEBUG, vault::read)
        val db = Room.databaseBuilder(context, DearbyDatabase::class.java, "dearby.db").build()
        try {
            val received = WalletRepository(http, db.dao()).wallet().filter { it.card.ownerId == args.getString("recipientId") }
            assertTrue(received.isNotEmpty()); assertTrue(received.all { it.reciprocal })
        } finally { db.close() }
    }
}
