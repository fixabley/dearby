package io.wid.dearby.adaptor.web.v1.tags

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException

// 쿼리 tagIds: 쉼표로 이은 태그 id 목록(native-v1.md "태그"). 비어 있으면 null, 숫자가 아니면 400
object TagIds {
    fun parse(value: String?): Set<Id>? {
        val parts = value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        if (parts.isEmpty()) return null
        return parts.map { it.toLongOrNull() ?: throw InvalidInputException("tagIds는 숫자 id의 쉼표 목록입니다: $it") }.toSet()
    }
}
