package com.dearby.nativeapp

import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.AccountContact
import com.dearby.nativeapp.entities.account.model.AccountHistory
import java.net.URL
import java.util.UUID
import com.dearby.nativeapp.app.AccountViewModel
import com.dearby.nativeapp.app.CardPublishViewModel
import com.dearby.nativeapp.app.PublishPhase
import com.dearby.nativeapp.entities.account.api.SessionStore
import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in: the real client against an isolated local API whose mail goes to a local Mailpit.
 * Set DEARBY_REAL_API_ORIGIN (http://127.0.0.1:4310) and DEARBY_REAL_MAILPIT (http://127.0.0.1:<port>); never production.
 */
class AccountRealApiTest {
    private val api = System.getenv("DEARBY_REAL_API_ORIGIN")
    private val mailpit = System.getenv("DEARBY_REAL_MAILPIT")

    private fun code(email: String): String {
        repeat(40) {
            val list = JSONObject(URL("$mailpit/api/v1/search?query=" + java.net.URLEncoder.encode("to:$email", "UTF-8")).readText())
            val messages = list.getJSONArray("messages")
            if (messages.length() > 0) {
                val text = JSONObject(URL("$mailpit/api/v1/message/" + messages.getJSONObject(0).getString("ID")).readText()).getString("Text")
                return Regex("""\d{6}""").find(text)!!.value
            }
            Thread.sleep(250)
        }
        error("no mail for $email")
    }

    @Test fun signInEditProfilePublishAndSignOut() = runBlocking {
        assumeTrue(api != null && mailpit != null)
        require(api!!.startsWith("http://127.0.0.1") && mailpit!!.startsWith("http://127.0.0.1")) { "local only" }
        val client = AccountClient(api)
        val email = "android-${UUID.randomUUID()}@example.test"
        val session = client.signIn(client.requestCode(email), code(email))
        assertEquals("", client.profile(session).name)
        val contact = AccountContact(UUID.randomUUID().toString(), "email", "이메일", email)
        val history = AccountHistory(UUID.randomUUID().toString(), "테스트 활동", "참가자", "2026-10-01", null, "")
        val saved = client.saveProfile(client.profile(session).copy(name = "안드로이드 테스트", contacts = listOf(contact), histories = listOf(history)), session)
        assertEquals(listOf(contact), saved.contacts)
        val card = client.publish("첫 명함", "", listOf(contact.id), emptyList(), session)
        assertEquals("안드로이드 테스트", card.profileName)
        assertEquals(listOf(contact.id), card.contacts.map { it.id })
        assertTrue(card.histories.isEmpty())
        val rejected = runCatching { client.publish("x", "", listOf(UUID.randomUUID().toString()), emptyList(), session) }.exceptionOrNull()
        assertEquals(AccountError.INVALID_INPUT, (rejected as AccountException).error)
        client.signOut(session)
        val expired = runCatching { client.profile(session) }.exceptionOrNull()
        assertEquals(AccountError.UNAUTHORIZED, (expired as AccountException).error)
    }

    /** 5b: a draft typed before signing in is merged onto the account's saved profile and published. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun guestDraftMergesOntoSavedProfileAndPublishes() = runBlocking {
        assumeTrue(api != null && mailpit != null)
        require(api!!.startsWith("http://127.0.0.1") && mailpit!!.startsWith("http://127.0.0.1")) { "local only" }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val store = object : SessionStore {
                var session: AccountSession? = null
                override fun load() = session
                override fun save(session: AccountSession) { this.session = session }
                override fun clear() { session = null }
            }
            val client = AccountClient(api)
            val account = AccountViewModel(client, store)
            val model = CardPublishViewModel(account)
            model.start()
            val email = "android-merge-${UUID.randomUUID()}@example.test"
            val draft = model.state.value.draft
            model.edit(draft.copy(name = "안드로이드 발행", contacts = draft.contacts.map { if (it.kind == "email") it.copy(value = email) else it }))
            account.requestCode(email)
            account.verify(code(email))
            val session = store.session!!
            val phone = AccountContact(UUID.randomUUID().toString(), "phone", "전화번호", "010-1234-5678")
            val history = AccountHistory(UUID.randomUUID().toString(), "저장된 활동", "", "2026-09-01", null, "")
            client.saveProfile(AccountProfile("저장된 이름", "", "", listOf(phone), listOf(history)), session)
            model.continueAfterSignIn()
            // The real transport hops to the IO dispatcher, so wait for the publish to finish.
            val phase = withTimeout(20_000) { model.state.first { it.phase is PublishPhase.Published || it.phase is PublishPhase.Failed } }.phase
            val card = (phase as PublishPhase.Published).card
            assertEquals("안드로이드 발행", card.profileName)
            assertEquals(listOf(email), card.contacts.map { it.value })
            assertTrue(card.histories.isEmpty())
            val stored = client.profile(session)
            assertEquals(setOf(phone.value, email), stored.contacts.map { it.value }.toSet())
            assertEquals(listOf(history.id), stored.histories.map { it.id })
            client.signOut(session)
        } finally { Dispatchers.resetMain() }
    }
}
