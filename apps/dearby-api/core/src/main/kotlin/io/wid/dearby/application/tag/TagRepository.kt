package io.wid.dearby.application.tag

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Tag

// 태그 사전과 프로그램·활동 연결(native-v1.md "태그(얻을 수 있는 것)"). 구현은 adaptor/persistence
interface TagRepository {
    // 사전 전체를 sort_order, id 순으로
    fun findAll(): List<Tag>
    // 없는 id는 결과에서 빠진다
    fun findAllById(ids: Collection<Id>): List<Tag>
    // 한글·영문 이름으로 찾는다(띄어쓰기·대소문자 무시). 사전에 없는 이름은 빠진다
    fun findByNames(names: Collection<String>): List<Tag>
    // 띄어쓰기만 다른 같은 이름이 있으면 그 태그를 돌려주고 만들지 않는다(admin "직접 입력")
    fun create(name: String, nameEn: String?): Tag
    // 보낸 값만 바꾼다. nameEn은 빈 문자열이면 지운다. 바뀐 행이 없으면(태그 없음) false
    fun update(id: Id, name: String?, nameEn: String?): Boolean
    // 연결(program_tags·activity_tags)도 함께 지워진다(FK cascade). 없으면 false
    fun delete(id: Id): Boolean
    // 프로그램 태그를 통째로 바꾼다
    fun setProgramTags(programId: Id, tagIds: Collection<Id>)
    // 활동 태그를 통째로 바꾼다. curate면 curated_fields에 tags를 남겨 수집기가 덮어쓰지 않게 한다
    fun setActivityTags(activityId: Id, tagIds: Collection<Id>, curate: Boolean)
    // 프로그램 태그를 운영자가 태그를 지정하지 않은(curated_fields에 tags가 없는) 그 프로그램의 활동에 합집합으로 더한다. 태그가 하나라도 더해진 활동 수
    fun addProgramTagsToActivities(programId: Id, tagIds: Collection<Id>): Int
}
