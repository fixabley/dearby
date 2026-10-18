package io.wid.dearby.adaptor.web.v1.cards

import io.wid.dearby.application.card.CardService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// native-v1.md "명함·지갑". GET /v1/cards/{id}는 인증 없이(ApiAccessRules), 나머지는 주인만
@RestController
@RequestMapping("/v1/cards")
class CardController(private val cards: CardService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt) = CardListResponse(cards.list(userId(jwt)).map { CardResponse.from(it, includeTemplate = true) })

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun publish(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: CardPublishRequest) =
        CardResponse.from(cards.publish(userId(jwt), request.name, request.description, request.template, request.contactIds, request.historyIds, request.webTemplate), includeTemplate = true)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID) = CardResponse.from(cards.get(id), includeTemplate = false)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) = cards.revoke(userId(jwt), id)


    private fun userId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
