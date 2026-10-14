package io.wid.dearby.adaptor.security

import io.wid.dearby.domain.user.User
import io.wid.dearby.domain.user.UserRole
import org.junit.jupiter.api.io.TempDir
import org.springframework.security.oauth2.jwt.BadJwtException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtKeyTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `키가 없으면 생성하고 다음 기동에서는 같은 키를 읽는다`() {
        val path = dir.resolve("nested/private.pem")

        val created = loadOrCreateRsaKey(path)
        val loaded = loadOrCreateRsaKey(path)

        assertEquals(created.keyID, loaded.keyID)
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path)))
    }

    @Test
    fun `발급한 토큰을 검증하고 다른 키로 서명한 토큰은 거부한다`() {
        val properties =
            JwtProperties(dir.resolve("a.pem"), "https://issuer.test", Duration.ofMinutes(5), Duration.ofDays(90))
        val config = JwtConfig()
        val key = config.rsaKey(properties)
        val user = User(UUID.randomUUID(), listOf(UserRole.USER))

        val token = JwtIssuer(config.jwtEncoder(key), properties).issueAccessToken(user)

        assertEquals(user.id.toString(), config.jwtDecoder(key, properties).decode(token).subject)
        val otherKey = loadOrCreateRsaKey(dir.resolve("b.pem"))
        assertFailsWith<BadJwtException> { config.jwtDecoder(otherKey, properties).decode(token) }
    }
}
