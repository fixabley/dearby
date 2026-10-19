package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.AdminTagService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 태그 사전 관리(native-v1.md "태그(얻을 수 있는 것)"). ADMIN만(AdminAccessRules)
@RestController
@RequestMapping("/v1/admin/tags")
class AdminTagController(private val tags: AdminTagService) {
    @GetMapping
    fun list() = TagListResponse(tags.list().map(TagResponse::from))

    // 같은 이름(띄어쓰기 무시)이 있으면 새로 만들지 않고 그 태그를 돌려준다(어드민 "직접 입력")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: TagRequest) = TagResponse.from(tags.create(request.name, request.nameEn))

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: TagRequest) = TagResponse.from(tags.update(id, request.name, request.nameEn))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = tags.delete(id)
}
