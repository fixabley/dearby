package com.dearby.nativeapp

import com.dearby.nativeapp.app.AccountPhase
import com.dearby.nativeapp.app.AccountViewModel
import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.api.HttpRequest
import com.dearby.nativeapp.entities.account.api.HttpResponse
import com.dearby.nativeapp.entities.account.api.SessionStore
import com.dearby.nativeapp.entities.account.api.Transport
import com.dearby.nativeapp.entities.account.model.AccountHistory
import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.entities.account.model.toJson
import com.dearby.nativeapp.app.CardPublishViewModel
import com.dearby.nativeapp.app.PublishPhase
import com.dearby.nativeapp.app.QrShareViewModel
import com.dearby.nativeapp.entities.account.model.ScannedLink
import com.dearby.nativeapp.features.scan.decodeQr
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.dearby.nativeapp.pages.qr.QrSharePhase
import com.dearby.nativeapp.shared.config.sharedCardId
import com.dearby.nativeapp.shared.config.sharedCardUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Records requests and answers from a script; stands in for the API in unit tests. */
private class FakeTransport(vararg answers: Pair<Int, String>) : Transport {
    val requests = mutableListOf<HttpRequest>()
    private val queue = answers.toMutableList()
    override suspend fun send(request: HttpRequest): HttpResponse {
        requests += request
        val (status, body) = if (queue.isEmpty()) 500 to "{}" else queue.removeAt(0)
        return HttpResponse(status, body)
    }
}
private class MemoryStore(var session: AccountSession? = null) : SessionStore {
    override fun load() = session
    override fun save(session: AccountSession) { this.session = session }
    override fun clear() { session = null }
}

class AccountTest {
    private val challenge = "e1000000-0000-4000-8000-000000000001"
    private fun client(fake: FakeTransport) = AccountClient("https://api.example.test", fake)

    @Test fun signInFlowStoresTheSessionAndSendsBearer() = runBlocking {
        val fake = FakeTransport(202 to """{"challengeId":"$challenge"}""", 200 to """{"sessionToken":"token-1","profileId":"p1"}""",
            200 to """{"id":"p1","name":"","job":"","introduction":"","contacts":[],"histories":[],"updatedAt":"2026-10-06T00:00:00Z"}""")
        val store = MemoryStore()
        val account = AccountViewModel(client(fake), store)
        assertEquals(AccountPhase.SIGNED_OUT, account.state.value.phase)
        account.requestCode("  Me@Example.Test ")
        assertEquals(AccountPhase.CODE_SENT, account.state.value.phase)
        assertEquals("https://api.example.test/v1/auth/challenges", fake.requests[0].url)
        assertEquals("""{"email":"me@example.test"}""", fake.requests[0].body)
        account.verify("123456")
        assertEquals(AccountPhase.SIGNED_IN, account.state.value.phase)
        assertEquals("token-1", store.session?.sessionToken)
        assertEquals("", account.authorized { account.client.profile(it) }.name)
        assertEquals("token-1", fake.requests[2].token)
        assertNull(fake.requests[0].token)
    }
    @Test fun wrongCodeKeepsTheChallengeAndBadInputNeverCallsTheApi() = runBlocking {
        val fake = FakeTransport(202 to """{"challengeId":"$challenge"}""", 401 to "{}")
        val account = AccountViewModel(client(fake), MemoryStore())
        account.requestCode("no-at-sign")
        assertTrue(fake.requests.isEmpty())
        account.requestCode("me@example.test")
        account.verify("12ab56")
        assertEquals(1, fake.requests.size)
        account.verify("000000")
        assertEquals(AccountPhase.CODE_SENT, account.state.value.phase)
        assertEquals("인증번호가 맞지 않거나 만료됐어요.", account.state.value.message)
    }
    @Test fun rateLimitAndExpiredSessionSignOut() = runBlocking {
        val fake = FakeTransport(401 to "{}", 429 to "{}")
        val store = MemoryStore(AccountSession("old", "p1"))
        val account = AccountViewModel(client(fake), store)
        assertEquals(AccountPhase.SIGNED_IN, account.state.value.phase)
        assertThrows(AccountException::class.java) { runBlocking { account.authorized { account.client.profile(it) } } }
        assertEquals(AccountPhase.SIGNED_OUT, account.state.value.phase)
        assertNull(store.session)
        account.requestCode("me@example.test")
        assertEquals("요청이 많아요. 잠시 후 다시 시도해 주세요.", account.state.value.message)
    }
    @Test fun signOutForgetsTheSessionEvenWhenTheServerFails() = runBlocking {
        val fake = FakeTransport(500 to "{}")
        val store = MemoryStore(AccountSession("t", "p1"))
        val account = AccountViewModel(client(fake), store)
        account.signOut()
        assertEquals("DELETE", fake.requests.first().method)
        assertNull(store.session)
        assertEquals(AccountPhase.SIGNED_OUT, account.state.value.phase)
    }
    @Test fun profileEncodesOngoingHistoryWithNullEndDateAndPublishSendsIds() = runBlocking {
        val json = AccountProfile("", "", "", emptyList(), listOf(AccountHistory("h1", "t", "r", "2026-01-01", null, ""))).toJson()
        assertTrue(json.getJSONArray("histories").getJSONObject(0).isNull("endDate"))
        assertTrue(json.getJSONArray("histories").getJSONObject(0).has("endDate"))
        val fake = FakeTransport(201 to """{"id":"c1","ownerId":"p1","name":"명함","description":"","profileName":"김","job":"","introduction":"","contacts":[],"histories":[],"createdAt":"2026-10-06T00:00:00Z"}""")
        val card = client(fake).publish("명함", "", listOf("a"), emptyList(), AccountSession("t", "p1"))
        assertEquals("c1", card.id)
        assertEquals("a", JSONObject(fake.requests[0].body!!).getJSONArray("contactIds").getString(0))
    }

    private val stored = """{"id":"p1","name":"저장된 이름","job":"","introduction":"","contacts":[{"id":"c-old","kind":"phone","label":"전화번호","value":"010-0000-0000"}],"histories":[{"id":"h-old","title":"캠프","role":"","startDate":"2025-01","endDate":null,"description":""}],"updatedAt":"2026-10-06T00:00:00Z"}"""
    private val cardBody = """{"id":"card-1","name":"내 명함","description":"","profileName":"김지민","job":"","contacts":[],"histories":[],"createdAt":"2026-10-06T00:00:00Z"}"""
    @OptIn(ExperimentalCoroutinesApi::class) @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @OptIn(ExperimentalCoroutinesApi::class) @After fun reset() = Dispatchers.resetMain()

    @Test fun guestPublishAsksSignInThenMergesAndKeepsUnseenRowsPrivate() = runBlocking {
        val fake = FakeTransport(202 to """{"challengeId":"$challenge"}""", 200 to """{"sessionToken":"t","profileId":"p1"}""",
            200 to stored, 200 to stored, 201 to cardBody)
        val account = AccountViewModel(client(fake), MemoryStore())
        val model = CardPublishViewModel(account)
        model.start()
        val draft = model.state.value.draft
        model.edit(draft.copy(name = "김지민", contacts = draft.contacts.map { if (it.kind == "email") it.copy(value = "me@example.test") else it }))
        model.publish()
        assertTrue(model.state.value.signingIn)
        assertTrue(fake.requests.isEmpty())
        account.requestCode("me@example.test")
        account.verify("123456")
        model.continueAfterSignIn()
        assertEquals(listOf("POST", "POST", "GET", "PUT", "POST"), fake.requests.map { it.method })
        val profile = JSONObject(fake.requests[3].body!!)
        assertEquals("김지민", profile.getString("name"))
        val values = (0 until profile.getJSONArray("contacts").length()).map { profile.getJSONArray("contacts").getJSONObject(it).getString("value") }
        assertEquals(setOf("010-0000-0000", "me@example.test"), values.toSet())
        assertEquals(1, profile.getJSONArray("histories").length())
        val card = JSONObject(fake.requests[4].body!!)
        assertEquals(1, card.getJSONArray("contactIds").length())
        assertNotEquals("c-old", card.getJSONArray("contactIds").getString(0))
        assertEquals(0, card.getJSONArray("historyIds").length())
        assertEquals("card-1", (model.state.value.phase as PublishPhase.Published).card.id)
    }
    @Test fun cardFailureAfterProfileSaveRetriesOnlyTheCard() = runBlocking {
        val fake = FakeTransport(200 to stored, 200 to stored, 503 to "{}", 201 to cardBody)
        val model = CardPublishViewModel(AccountViewModel(client(fake), MemoryStore(AccountSession("t", "p1"))))
        model.start()
        assertEquals("저장된 이름", model.state.value.draft.name)
        model.edit(model.state.value.draft.copy(job = "기획"))
        model.publish()
        assertEquals(PublishPhase.Failed("프로필은 저장했어요. 명함 발행만 다시 시도해 주세요."), model.state.value.phase)
        model.publish()
        assertEquals(listOf("GET", "PUT", "POST", "POST"), fake.requests.map { it.method })
        assertTrue(model.state.value.phase is PublishPhase.Published)
    }

    @Test fun shareLinkRoundTripsThroughTheParser() {
        val url = sharedCardUrl("5a1e0000-0000-4000-8000-000000000001", "https://dearby.example.test")
        assertEquals("https://dearby.example.test/s/5a1e0000-0000-4000-8000-000000000001", url)
        assertEquals("5a1e0000-0000-4000-8000-000000000001", sharedCardId(url, "https://dearby.example.test"))
    }
    @Test fun qrShareUsesNewestCardAndReusesASharePerActivityChoice() = runBlocking {
        val share = """{"id":"5a1e0000-0000-4000-8000-000000000001","cardId":"card-2","activities":[],"createdAt":"2026-10-06T00:00:00Z"}"""
        val other = """{"id":"5a1e0000-0000-4000-8000-000000000002","cardId":"card-2","activities":[{"id":"a1","title":"활동"}],"createdAt":"2026-10-06T00:00:00Z"}"""
        val fake = FakeTransport(200 to """{"items":[$cardBody,${cardBody.replace("card-1", "card-2")}]}""", 201 to share, 201 to other)
        val model = QrShareViewModel(AccountViewModel(client(fake), MemoryStore(AccountSession("t", "p1")))) { sharedCardUrl(it, "https://dearby.example.test") }
        model.load()
        assertEquals("card-2", model.state.value.selectedCardId)
        assertEquals("https://dearby.example.test/s/5a1e0000-0000-4000-8000-000000000001", model.state.value.url)
        assertEquals("https://api.example.test/v1/cards/card-2/shares", fake.requests[1].url)
        assertEquals("""{"activityIds":[]}""", fake.requests[1].body)
        model.toggle("a1")
        assertEquals("""{"activityIds":["a1"]}""", fake.requests[2].body)
        model.toggle("a1")
        assertEquals(3, fake.requests.size)
        assertEquals("https://dearby.example.test/s/5a1e0000-0000-4000-8000-000000000001", model.state.value.url)
    }
    @Test fun qrShareSignedOutOrWithoutCardsAsksToMakeOne() = runBlocking {
        val fake = FakeTransport(200 to """{"items":[]}""")
        val signedOut = QrShareViewModel(AccountViewModel(client(fake), MemoryStore())) { it }
        signedOut.load()
        assertEquals(QrSharePhase.SIGNED_OUT, signedOut.state.value.phase)
        assertTrue(fake.requests.isEmpty())
        val empty = QrShareViewModel(AccountViewModel(client(fake), MemoryStore(AccountSession("t", "p1")))) { it }
        empty.load()
        assertEquals(QrSharePhase.NO_CARD, empty.state.value.phase)
    }

    @Test fun scannedTextBecomesAShareOrALegacyCardOnly() {
        val web = "https://dearby.example.test"
        val id = "5A1E0000-0000-4000-8000-000000000001"
        assertEquals(ScannedLink.Share(id.lowercase()), ScannedLink.parse("$web/s/$id", web))
        assertEquals(ScannedLink.Card(id.lowercase()), ScannedLink.parse(" dearby://card/$id\n", web))
        for (text in listOf("https://evil.test/s/$id", "dearby://card/not-a-uuid", "dearby://share/$id", "dearby://card/$id?x=1", "hello"))
            assertNull(text, ScannedLink.parse(text, web))
    }
    @Test fun cameraFrameQrReadsBackItsText() {
        val matrix = QRCodeWriter().encode("https://dearby.example.test/s/x", BarcodeFormat.QR_CODE, 200, 200)
        val luminance = ByteArray(matrix.width * matrix.height) { if (matrix[it % matrix.width, it / matrix.width]) 0 else -1 }
        assertEquals("https://dearby.example.test/s/x", decodeQr(luminance, matrix.width, matrix.height))
        assertNull(decodeQr(ByteArray(100 * 100) { -1 }, 100, 100))
    }
    @Test fun publicShareNeedsNoSessionAndMissingIsDistinct() = runBlocking {
        val share = """{"share":{"id":"s1","cardId":"card-1","activities":[{"id":"a1","title":"활동"}],"createdAt":"2026-10-06T00:00:00Z"},"card":$cardBody}"""
        val fake = FakeTransport(200 to share, 404 to "{}")
        val received = client(fake).publicShare("s1")
        assertEquals(listOf("활동"), received.share.activities.map { it.title })
        assertNull(fake.requests[0].token)
        val missing = runCatching { client(fake).publicCard("card-9") }.exceptionOrNull() as AccountException
        assertEquals(AccountError.NOT_FOUND, missing.error)
        assertEquals("https://api.example.test/v1/cards/card-9", fake.requests[1].url)
    }
}
