package io.wid.dearby.adaptor.persistence

import io.wid.dearby.domain.ContactKind
import io.wid.dearby.domain.profile.*
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

// 연락처·이력을 jsonb로 넣고 꺼내는 공통 형식. 프로필 버전 스냅샷과 명함 스냅샷이 함께 쓴다
object ProfileJson {

    private val json = JsonMapper.builder().findAndAddModules().build()

    fun contacts(contacts: List<Contact>): String = json.writeValueAsString(contacts.map { ContactJson(it.id, it.kind.name, it.label, it.value, it.verified, it.verifiedAt) })

    fun readContacts(value: String): List<Contact> =
        json.readValue<List<ContactJson>>(value).map { Contact(it.id, ContactKind.valueOf(it.kind), it.label, it.value, it.verified, it.verifiedAt) }

    fun histories(histories: List<History>): String = json.writeValueAsString(histories.map(HistoryJson::from))

    fun readHistories(value: String): List<History> = json.readValue<List<HistoryJson>>(value).map { it.toHistory() }

    fun jobs(jobs: List<ProfileJob>): String = json.writeValueAsString(jobs.map { ProfileJobJson(it.id, it.job?.let { r -> RefJson(r.id, r.name) }, it.jobName, it.approval.name, it.approvalReason) })

    fun readJobs(value: String): List<ProfileJob> = json.readValue<List<ProfileJobJson>>(value).map {
        ProfileJob(it.id, it.job?.let { r -> ActivityInfoRef(r.id, r.name) }, it.jobName, ApprovalStatus.valueOf(it.approval), it.approvalReason)
    }

    fun <T : Any> write(value: T): String = json.writeValueAsString(value)

    inline fun <reified T : Any> read(value: String): T = mapper().readValue<T>(value)

    fun mapper(): JsonMapper = json

    // verified·verifiedAt은 2026-10-26(V64) 전 스냅샷·명함에 없어 기본값으로 읽는다
    data class ContactJson(val id: UUID, val kind: String, val label: String, val value: String, val verified: Boolean = false, val verifiedAt: OffsetDateTime? = null)

    data class RefJson(val id: Long, val name: String)

    data class ProfileJobJson(val id: UUID, val job: RefJson?, val jobName: String?, val approval: String, val approvalReason: String?)

    data class EvidenceJson(val id: UUID, val fileName: String, val contentType: String, val size: Int, val uploadedAt: OffsetDateTime)

    data class OwnerJson(val kind: String, val id: Long?, val name: String)

    // V67(역할 귀속) 전 스냅샷에는 owner가 없고 kind·organizationName·programName·activityName이 있다. 읽을 때 활동 → 프로그램 → 조직 순으로 귀속을 만들고,
    // 등록된 것이 없으면 직접 적은 이름만 남긴다(id 없음). 쓸 때는 새 모양만 쓴다
    data class HistoryJson(
        val id: UUID,
        val role: RefJson?,
        val roleName: String?,
        val owner: OwnerJson? = null,
        val organization: RefJson?,
        val program: RefJson?,
        val activity: RefJson?,
        val startDate: LocalDate,
        val endDate: LocalDate?,
        val description: String,
        val approval: String,
        val approvalReason: String?,
        val verification: String,
        val evidence: List<EvidenceJson> = emptyList(),
        val kind: String? = null,
        val organizationName: String? = null,
        val programName: String? = null,
        val activityName: String? = null,
    ) {
        fun toHistory(): History {
            val owner = owner?.let { RoleOwner(RoleOwnerKind.valueOf(it.kind), it.id, it.name) } ?: when {
                activity != null -> RoleOwner(RoleOwnerKind.ACTIVITY, activity.id, activity.name)
                program != null -> RoleOwner(RoleOwnerKind.PROGRAM, program.id, program.name)
                organization != null -> RoleOwner(RoleOwnerKind.ORGANIZATION, organization.id, organization.name)
                kind == "ACTIVITY" -> RoleOwner(RoleOwnerKind.ACTIVITY, null, activityName ?: organizationName ?: "")
                else -> RoleOwner(RoleOwnerKind.ORGANIZATION, null, organizationName ?: "")
            }
            return History(
                id, role?.let { ActivityInfoRef(it.id, it.name) }, roleName ?: role?.name ?: "", owner,
                organization?.let { ActivityInfoRef(it.id, it.name) },
                program?.let { ActivityInfoRef(it.id, it.name) },
                activity?.let { ActivityInfoRef(it.id, it.name) },
                startDate, endDate, description,
                ApprovalStatus.valueOf(approval), approvalReason, VerificationStatus.valueOf(verification),
                evidence.map { EvidenceMeta(it.id, it.fileName, it.contentType, it.size, it.uploadedAt) },
            )
        }

        companion object {
            fun from(h: History) = HistoryJson(
                h.id,
                h.role?.let { RefJson(it.id, it.name) }, h.roleName,
                OwnerJson(h.owner.kind.name, h.owner.id, h.owner.name),
                h.organization?.let { RefJson(it.id, it.name) },
                h.program?.let { RefJson(it.id, it.name) },
                h.activity?.let { RefJson(it.id, it.name) },
                h.startDate, h.endDate, h.description,
                h.approval.name, h.approvalReason, h.verification.name,
                h.evidence.map { EvidenceJson(it.id, it.fileName, it.contentType, it.size, it.uploadedAt) },
            )
        }
    }
}
