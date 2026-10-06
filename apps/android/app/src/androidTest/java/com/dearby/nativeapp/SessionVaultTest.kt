package com.dearby.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.entities.account.api.SessionVault
import com.dearby.nativeapp.entities.account.model.AccountSession
import org.junit.Assert.*
import org.junit.Test

class SessionVaultTest {
    @Test fun keystoreVaultRoundTripsUnderItsOwnNames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = SessionVault(context, "account.session.test")
        vault.clear()
        assertNull(vault.load())
        vault.save(AccountSession("secret", "p1"))
        assertEquals(AccountSession("secret", "p1"), vault.load())
        val raw = context.getSharedPreferences("account.session.test", 0).getString("ciphertext", "")!!
        assertFalse("token must not be stored in plain text", raw.contains("secret"))
        vault.clear()
        assertNull(vault.load())
    }
}
