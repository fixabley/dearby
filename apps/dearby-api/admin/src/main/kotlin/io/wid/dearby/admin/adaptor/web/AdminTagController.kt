package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.AdminTagService
import io.wid.dearby.admin.application.AdminTagService.ParentChange
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.TagKind
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
import tools.jackson.databind.JsonNode

// 태그 사전 관리(native-v1.md "태그(얻을 수 있는 것)"). ADMIN만(AdminAccessRules)
@RestController
@RequestMapping("/v1/admin/tags")
class AdminTagController(private val tags: AdminTagService) {
    @GetMapping
    fun list() = TagListResponse(tags.list().map(TagResponse::from))

    // 같은 이름(띄어쓰기 무시)이 있으면 새로 만들지 않고 그 태그를 돌려준다(어드민 "직접 입력")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: TagRequest) =
        TagResponse.from(tags.create(request.name, request.nameEn, kind(request.kind), request.parentId?.takeUnless { it.isNull }?.let(::parentId)))

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: TagRequest) = TagResponse.from(
        tags.update(id, request.name, request.nameEn, kind(request.kind), request.parentId?.let { if (it.isNull) ParentChange.Clear else ParentChange.Set(parentId(it)) } ?: ParentChange.Keep)
    )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = tags.delete(id)

    private fun kind(value: String?): TagKind? =
        value?.let { code -> TagKind.entries.firstOrNull { it.name == code } ?: throw InvalidInputException("태그 종류는 FIELD 또는 TRAIT입니다") }

    private fun parentId(node: JsonNode): Long =
        node.takeIf { it.isIntegralNumber }?.asLong() ?: throw InvalidInputException("parentId는 태그 id여야 합니다")
}
