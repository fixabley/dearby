package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_VERSIONS
import io.wid.dearby.application.profile.ProfileVersionRepository
import io.wid.dearby.domain.ContactKind
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.*
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 프로필 스냅샷을 JSON으로 둔다. 되돌릴 때 입력으로 쓰므로 이름·값·카탈로그 참조를 모두 남긴다(인증자료 목록은 메타만)
@Repository
class JooqProfileVersionRepository(private val dsl: DSLContext) : ProfileVersionRepository {

    private val json = JsonMapper.builder().findAndAddModules().build()

    override fun append(profile: Profile) {
        dsl.insertInto(PROFILE_VERSIONS)
            .set(PROFILE_VERSIONS.USER_ID, profile.userId)
            .set(PROFILE_VERSIONS.VERSION, profile.version)
            .set(PROFILE_VERSIONS.SNAPSHOT, JSONB.valueOf(json.writeValueAsString(Snapshot.from(profile))))
            .set(PROFILE_VERSIONS.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
    }

    override fun list(userId: UserId, after: Int?, size: Int): List<Pair<Int, OffsetDateTime>> =
        dsl.select(PROFILE_VERSIONS.VERSION, PROFILE_VERSIONS.CREATED_AT).from(PROFILE_VERSIONS)
            .where(PROFILE_VERSIONS.USER_ID.eq(userId), after?.let { PROFILE_VERSIONS.VERSION.lt(it) } ?: org.jooq.impl.DSL.noCondition())
            .orderBy(PROFILE_VERSIONS.VERSION.desc()).limit(size).fetch()
            .map { it.value1()!! to it.value2()!! }

    override fun find(userId: UserId, version: Int): ProfileVersion? {
        val row = dsl.selectFrom(PROFILE_VERSIONS).where(PROFILE_VERSIONS.USER_ID.eq(userId), PROFILE_VERSIONS.VERSION.eq(version)).fetchOne() ?: return null
        val snapshot = json.readValue<Snapshot>(row.snapshot!!.data())
        return ProfileVersion(version, row.createdAt!!, snapshot.toProfile(userId, version, row.createdAt!!))
    }

    private data class Snapshot(
        val name: String,
        val job: String,
        val introduction: String,
        val contacts: List<ContactSnapshot>,
        val histories: List<HistorySnapshot>,
    ) {
        fun toProfile(userId: UserId, version: Int, at: OffsetDateTime) = Profile(
            userId, name, job, introduction,
            contacts.map { Contact(it.id, ContactKind.valueOf(it.kind), it.label, it.value) },
            histories.map { it.toHistory() },
            version, at,
        )

        companion object {
            fun from(p: Profile) = Snapshot(
                p.name, p.job, p.introduction,
                p.contacts.map { ContactSnapshot(it.id, it.kind.name, it.label, it.value) },
                p.histories.map { HistorySnapshot.from(it) },
            )
        }
    }

    private data class ContactSnapshot(val id: UUID, val kind: String, val label: String, val value: String)

    private data class RefSnapshot(val id: Long, val name: String)

    private data class EvidenceSnapshot(val id: UUID, val fileName: String, val contentType: String, val size: Int, val uploadedAt: OffsetDateTime)

    private data class HistorySnapshot(
        val id: UUID,
        val kind: String,
        val organization: RefSnapshot?,
        val organizationName: String?,
        val program: RefSnapshot?,
        val programName: String?,
        val activity: RefSnapshot?,
        val activityName: String?,
        val role: RefSnapshot?,
        val roleName: String?,
        val startDate: LocalDate,
        val endDate: LocalDate?,
        val description: String,
        val approval: String,
        val approvalReason: String?,
        val verification: String,
        val evidence: List<EvidenceSnapshot>,
    ) {
        fun toHistory() = History(
            id, HistoryKind.valueOf(kind),
            organization?.let { CatalogRef(it.id, it.name) }, organizationName,
            program?.let { CatalogRef(it.id, it.name) }, programName,
            activity?.let { CatalogRef(it.id, it.name) }, activityName,
            role?.let { CatalogRef(it.id, it.name) }, roleName,
            startDate, endDate, description,
            ApprovalStatus.valueOf(approval), approvalReason, VerificationStatus.valueOf(verification),
            evidence.map { EvidenceMeta(it.id, it.fileName, it.contentType, it.size, it.uploadedAt) },
        )

        companion object {
            fun from(h: History) = HistorySnapshot(
                h.id, h.kind.name,
                h.organization?.let { RefSnapshot(it.id, it.name) }, h.organizationName,
                h.program?.let { RefSnapshot(it.id, it.name) }, h.programName,
                h.activity?.let { RefSnapshot(it.id, it.name) }, h.activityName,
                h.role?.let { RefSnapshot(it.id, it.name) }, h.roleName,
                h.startDate, h.endDate, h.description,
                h.approval.name, h.approvalReason, h.verification.name,
                h.evidence.map { EvidenceSnapshot(it.id, it.fileName, it.contentType, it.size, it.uploadedAt) },
            )
        }
    }
}
