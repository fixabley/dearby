package com.dearby.nativeapp

import com.dearby.nativeapp.app.AccountPhase
import com.dearby.nativeapp.app.AccountViewModel
import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.api.HttpRequest
import com.dearby.nativeapp.entities.account.api.HttpResponse
import com.dearby.nativeapp.entities.account.api.SessionStore
import com.dearby.nativeapp.entities.account.api.Transport
import com.dearby.nativeapp.entities.account.model.AccountHistory
import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.entities.account.model.toJson
import kotlinx.coroutines.runBlocking
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
}
