package com.dearby.nativeapp

import com.dearby.nativeapp.entities.card.api.CardRepository
import com.dearby.nativeapp.entities.card.model.*
import com.dearby.nativeapp.entities.profile.api.ProfileRepository
import com.dearby.nativeapp.entities.profile.model.ProfileModel
import com.dearby.nativeapp.features.wallet.WalletRepository
import com.dearby.nativeapp.features.guest.GuestStore
import com.dearby.nativeapp.shared.api.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class HttpContractTest {
    private fun server(block: (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
        try { block(server, "http://127.0.0.1:${server.address.port}") } finally { server.stop(0) }
    }
    @Test fun publicationUsesBearerAndSelectionBody() = server { server, base -> runTest {
        var body = ""; var auth = ""
        server.createContext("/v1/cards") { exchange ->
            body = exchange.requestBody.bufferedReader().readText(); auth = exchange.requestHeaders.getFirst("Authorization")
            val json = """{"id":"c","ownerId":"p","name":"work","description":"","profileName":"name","job":"job","introduction":"","contacts":[],"histories":[],"createdAt":"2026-09-27T00:00:00Z"}""".toByteArray()
            exchange.sendResponseHeaders(201, json.size.toLong()); exchange.responseBody.use { it.write(json) }
        }
        val repository = CardRepository(HttpClient(base, true) { "test-token" }, MemoryDao())
        repository.publish(CardSelectionModel("work", "", setOf("visible-id"), emptySet()))
        assertEquals("Bearer test-token", auth); assertTrue(body.contains("visible-id")); assertFalse(body.contains("sessionToken"))
    } }
    @Test fun importHttpFailurePreservesAllLocalIds() = server { server, base -> runTest {
        server.createContext("/v1/wallet/import") { exchange -> exchange.sendResponseHeaders(503, -1); exchange.close() }
        val dao = MemoryDao(); val store = GuestStore(dao); val id = "00000000-0000-0000-0000-000000000001"
        store.save(id, ExchangeContextModel())
        try { WalletRepository(HttpClient(base, true) { "test" }, dao).import(store.all()); fail("Should fail") } catch (failure: ApiFailure) { assertEquals(503, failure.status) }
        assertEquals(id, store.all().single().cardId)
    } }
    @Test fun ambiguousDeliveryRetryKeepsRequestIdAfterRepositoryRecreation() = server { server, base -> runTest {
        val bodies = mutableListOf<String>()
        server.createContext("/v1/exchanges") { exchange ->
            bodies += exchange.requestBody.bufferedReader().readText()
            if (bodies.size == 1) { exchange.sendResponseHeaders(503, -1); exchange.close() }
            else {
                val response = """{"receiptId":"receipt","deliveredAt":"2026-09-27T00:00:00Z"}""".toByteArray()
                exchange.sendResponseHeaders(201, response.size.toLong()); exchange.responseBody.use { it.write(response) }
            }
        }
        val dao = MemoryDao(); val http = HttpClient(base, true) { "test" }
        try { WalletRepository(http, dao).send("c", "p", ExchangeContextModel(), "account"); fail() } catch (_: ApiFailure) { }
        assertNotNull(dao.document("account:account:pending-send"))
        WalletRepository(http, dao).send("c", "p", ExchangeContextModel(), "account")
        assertEquals(bodies[0], bodies[1]); assertNull(dao.document("account:account:pending-send"))
    } }
    @Test fun accountCachesAreIsolatedAndDraftSurvivesClear() = runTest {
        val dao = MemoryDao(); val repo = ProfileRepository(HttpClient("", false) { null }, dao)
        repo.saveDraft(ProfileModel(name = "local"))
        dao.put(com.dearby.nativeapp.shared.storage.DocumentRecord("account:A:profile", """{"id":"A","name":"private"}"""))
        repo.accountId = "B"; assertEquals("", repo.localProfile(true).name)
        repo.accountId = "A"; assertEquals("private", repo.localProfile(true).name)
        repo.clearAccount(); assertEquals("", repo.localProfile(true).name); assertEquals("local", repo.localProfile(false).name)
    }
    @Test fun productionRejectsHttp() = runTest {
        try { HttpClient("http://localhost", false) { null }.request("GET", "/cards/a", authenticated = false); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun missingServerCannotPretendToAuthenticate() = runTest {
        try { HttpClient("", true) { null }.request("POST", "/auth/sessions", "{}", false); fail() } catch (_: IllegalArgumentException) { }
    }
}
