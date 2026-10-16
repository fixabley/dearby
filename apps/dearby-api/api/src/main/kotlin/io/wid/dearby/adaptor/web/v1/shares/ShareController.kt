package io.wid.dearby.adaptor.web.v1.shares

import io.wid.dearby.adaptor.web.v1.cards.CardResponse
import io.wid.dearby.adaptor.web.v1.cards.CardShareResponse
import io.wid.dearby.application.card.ShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

// 인증 없이 받는다(ApiAccessRules). 공유 id가 공개 링크 /s/<id>다
@RestController
@RequestMapping("/v1/shares")
class ShareController(private val shares: ShareService) {

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): SharePageResponse {
        val (share, card) = shares.get(id)
        return SharePageResponse(CardShareResponse.from(share), CardResponse.from(card, includeTemplate = false))
    }
}
