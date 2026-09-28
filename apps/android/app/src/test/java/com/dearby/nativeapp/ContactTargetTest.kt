package com.dearby.nativeapp

import com.dearby.nativeapp.features.contact.contactAction
import org.junit.Assert.*
import org.junit.Test

class ContactTargetTest {
    @Test fun supportsExplicitPhoneEmailAndHttps() {
        assertEquals("tel:+821012345678", contactAction("a", "phone", "", "+82 (10) 1234-5678").target)
        assertEquals("mailto:sample@example.com", contactAction("a", "email", "", "sample@example.com").target)
        assertEquals("https://github.com/example", contactAction("a", "github", "", "https://github.com/example").target)
    }
    @Test fun malformedAndUntrustedSchemesAreCopyOnly() {
        listOf("javascript:alert(1)", "intent://evil", "http://example.com", "https://trusted@evil.example", "https://example.com:8080").forEach { assertNull(contactAction("a", "github", "", it).target) }
        assertNull(contactAction("a", "email", "", "sample@example.com?subject=surprise").target)
        assertNull(contactAction("a", "phone", "", "123;456").target)
        assertNull(contactAction("a", "kakao", "카카오 ID", "example-id").target)
    }
}
