package io.wid.dearby.adaptor.web

import io.wid.dearby.application.TokenService
import io.wid.dearby.domain.TokenPair
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RefreshRequest(val refreshToken: String)

@RestController
@RequestMapping("/v1/auth")
class AuthController(private val tokens: TokenService) {

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<TokenPair> =
        tokens.refresh(request.refreshToken)?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
}
