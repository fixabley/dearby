package io.wid.dearby.admin.application

import io.wid.dearby.application.organization.OrganizationLogoRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.LogoFile
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.OrganizationLogo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 조직 로고 올리기·보기·지우기(admin-v1.md "조직 로고"). 공개 이미지는 api 앱의 GET /v1/organizations/{id}/logo
@Service
class AdminOrganizationLogoService(private val organizations: OrganizationRepository, private val logos: OrganizationLogoRepository) {

    @Transactional
    fun put(organizationId: Id, contentType: String?, data: ByteArray): String {
        if (organizations.findAllById(listOf(organizationId)).isEmpty()) {
            throw NotFoundException("조직을 찾을 수 없습니다", "Organization not found: $organizationId")
        }
        val type = LogoFile.contentType(contentType, data)
        val updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
        logos.save(OrganizationLogo(organizationId, type, data, updatedAt))
        return OrganizationLogo.url(organizationId, updatedAt)
    }

    @Transactional(readOnly = true)
    fun get(organizationId: Id): OrganizationLogo =
        logos.find(organizationId) ?: throw NotFoundException("로고가 없습니다", "Organization logo not found: $organizationId")

    // 없어도 그대로 끝난다(204)
    @Transactional
    fun delete(organizationId: Id) = logos.delete(organizationId)
}
