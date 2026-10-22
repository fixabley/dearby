package io.wid.dearby.application.catalog

import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.application.collection.CollectionFeedbackRepository
import io.wid.dearby.application.collection.CollectionSettingsRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.collection.CollectionUrlTemplate
import io.wid.dearby.domain.collection.FeedbackKind
import io.wid.dearby.domain.collection.NewFeedback
import io.wid.dearby.domain.curation.CollectionSettings
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.net.URI

// 카탈로그 쓰기 규칙(admin-v1.md "조직"·"프로그램"·"활동" 삭제). 어드민과 batch 가져오기(admin-v1.md "운영 데이터 가져오기")가 같은 검증을 쓴다.
// 트랜잭션은 부르는 쪽이 연다
@Service
class CatalogWrites(
    private val organizations: OrganizationRepository,
    private val programs: ProgramRepository,
    private val settings: CollectionSettingsRepository,
    private val curations: ActivityCurationRepository,
    private val feedback: CollectionFeedbackRepository,
) {

    fun createOrganization(name: String, nameEn: String?, parentOrganizationId: Id?): Id {
        val trimmed = requireName(name)
        val english = optionalText(nameEn, null)
        parentOrganizationId?.let(::requireOrganization)
        requireUniqueOrganizationName(parentOrganizationId, trimmed, english, null)
        return unique { organizations.create(trimmed, english, parentOrganizationId) }
    }

    fun createProgram(organizationId: Id, name: String, nameEn: String?, collection: CollectionSettings?): Id {
        val trimmed = requireName(name)
        val english = optionalText(nameEn, null)
        requireOrganization(organizationId)
        requireUniqueProgramName(organizationId, trimmed, english, null)
        val id = unique { programs.create(organizationId, trimmed, english) }
        collection?.let { settings.save(id, validate(it)) }
        return id
    }

    fun saveCollectionSettings(programId: Id, collection: CollectionSettings) = settings.save(programId, validate(collection))

    // 수집으로 생긴 활동은 출처를 남겨 다음 수집에서 다시 만들지 않고, 이름을 수집 피드백 DRAFT_DISMISSED로 남긴다
    fun deleteActivity(id: Id) {
        // 수집 피드백(V43): 지우기 전에 이름을 읽어 둔다(지우면 사라진다). 운영자가 직접 만든 활동은 남기지 않는다
        val collected = feedback.collectedActivity(id)
        if (!curations.delete(id)) throw NotFoundException("활동을 찾을 수 없습니다", "Activity not found: $id")
        collected?.let { feedback.record(NewFeedback(FeedbackKind.DRAFT_DISMISSED, it.programId, it.host, it.name, emptyMap(), activityId = id)) }
    }

    // 같은 계위에서 띄어쓰기를 뺀 이름이 같으면 중복(2026-10-15 사용자 결정). 영문 이름도 같은 규칙으로 영문 이름끼리 비교한다(V13)
    fun requireUniqueOrganizationName(parentOrganizationId: Id?, name: String, nameEn: String?, exceptId: Id?) {
        fun duplicate(kind: String) = InvalidInputException(
            if (parentOrganizationId == null) "같은 ${kind}의 최상위 조직이 있습니다" else "같은 상위 조직 아래에 같은 ${kind}의 조직이 있습니다"
        )
        if (organizations.existsSiblingName(parentOrganizationId, name, exceptId)) throw duplicate("이름")
        if (nameEn != null && organizations.existsSiblingName(parentOrganizationId, nameEn, exceptId, english = true)) throw duplicate("영문 이름")
    }

    fun requireUniqueProgramName(organizationId: Id, name: String, nameEn: String?, exceptId: Id?) {
        if (programs.existsName(organizationId, name, exceptId)) throw InvalidInputException("같은 조직에 같은 이름의 프로그램이 있습니다")
        if (nameEn != null && programs.existsName(organizationId, nameEn, exceptId, english = true)) {
            throw InvalidInputException("같은 조직에 같은 영문 이름의 프로그램이 있습니다")
        }
    }

    // 확인과 저장 사이에 같은 이름이 동시에 들어오면 DB 유일 인덱스(V12)가 막는다. 같은 안내로 바꾼다
    fun <T> unique(write: () -> T): T = try {
        write()
    } catch (e: DuplicateKeyException) {
        throw InvalidInputException("같은 계위에 같은 이름이 있습니다", e.message ?: "duplicate name")
    }

    fun requireOrganization(id: Id) {
        if (organizations.findAllById(listOf(id)).isEmpty()) {
            throw InvalidInputException("조직을 찾을 수 없습니다", "Organization not found: $id")
        }
    }

    fun validate(collection: CollectionSettings): CollectionSettings {
        val url = collection.officialUrl?.trim()?.ifEmpty { null }
        // 주소 토큰(admin-v1.md): 모르는 {…}는 400, https 검사는 토큰을 샘플 값으로 바꾼 주소로 한다
        url?.let { CollectionUrlTemplate.unknownTokens(it).firstOrNull() }?.let { throw InvalidInputException("알 수 없는 주소 토큰입니다: {$it}") }
        val sample = url?.let(CollectionUrlTemplate::sample)
        if (sample != null && runCatching { URI(sample) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrEmpty() } != true) {
            throw InvalidInputException("공식 안내 페이지는 https 주소여야 합니다")
        }
        val hosts = collection.allowedHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        hosts.firstOrNull { !HOST.matches(it) }?.let { throw InvalidInputException("허용 호스트는 도메인 이름만 씁니다: $it") }
        return collection.copy(officialUrl = url, allowedHosts = hosts)
    }

    // null이면 지금 값 그대로, 빈 문자열이면 지운다(선택 입력인 영문 값)
    fun optionalText(value: String?, current: String?): String? = if (value == null) current else value.trim().ifEmpty { null }

    fun requireName(name: String): String = name.trim().ifEmpty { throw InvalidInputException("이름을 입력해 주세요") }

    private companion object {
        val HOST = Regex("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
    }
}
