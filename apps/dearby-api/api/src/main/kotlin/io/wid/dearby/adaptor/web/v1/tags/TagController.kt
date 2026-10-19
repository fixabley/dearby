package io.wid.dearby.adaptor.web.v1.tags

import io.wid.dearby.application.tag.TagRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 태그 사전(native-v1.md "태그"). 인증 없이 받는다(ApiAccessRules). 발견 탭의 태그 칩 줄이 쓴다
@RestController
@RequestMapping("/v1/tags")
class TagController(private val tags: TagRepository) {
    @GetMapping
    fun list() = TagListResponse(tags.findAll().map(TagResponse::from))
}
