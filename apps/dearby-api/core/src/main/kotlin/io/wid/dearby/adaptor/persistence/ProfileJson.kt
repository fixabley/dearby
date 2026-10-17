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

    fun contacts(contacts: List<Contact>): String = json.writeValueAsString(contacts.map { ContactJson(it.id, it.kind.name, it.label, it.value) })

    fun readContacts(value: String): List<Contact> = json.readValue<List<ContactJson>>(value).map { Contact(it.id, ContactKind.valueOf(it.kind), it.label, it.value) }

    fun histories(histories: List<History>): String = json.writeValueAsString(histories.map(HistoryJson::from))

    fun readHistories(value: String): List<History> = json.readValue<List<HistoryJson>>(value).map { it.toHistory() }

    fun jobs(jobs: List<ProfileJob>): String = json.writeValueAsString(jobs.map { ProfileJobJson(it.id, it.job?.let { r -> RefJson(r.id, r.name) }, it.jobName, it.approval.name, it.approvalReason) })

    fun readJobs(value: String): List<ProfileJob> = json.readValue<List<ProfileJobJson>>(value).map {
        ProfileJob(it.id, it.job?.let { r -> ActivityInfoRef(r.id, r.name) }, it.jobName, ApprovalStatus.valueOf(it.approval), it.approvalReason)
    }

    fun <T : Any> write(value: T): String = json.writeValueAsString(value)

    inline fun <reified T : Any> read(value: String): T = mapper().readValue<T>(value)

    fun mapper(): JsonMapper = json

    data class ContactJson(val id: UUID, val kind: String, val label: String, val value: String)

    data class RefJson(val id: Long, val name: String)

    data class ProfileJobJson(val id: UUID, val job: RefJson?, val jobName: String?, val approval: String, val approvalReason: String?)

    data class EvidenceJson(val id: UUID, val fileName: String, val contentType: String, val size: Int, val uploadedAt: OffsetDateTime)

    data class HistoryJson(
        val id: UUID,
        val kind: String,
        val organization: RefJson?,
        val organizationName: String?,
        val program: RefJson?,
        val programName: String?,
        val activity: RefJson?,
        val activityName: String?,
        val role: RefJson?,
        val roleName: String?,
        val startDate: LocalDate,
        val endDate: LocalDate?,
        val description: String,
        val approval: String,
        val approvalReason: String?,
        val verification: String,
        val evidence: List<EvidenceJson> = emptyList(),
    ) {
        fun toHistory() = History(
            id, HistoryKind.valueOf(kind),
            organization?.let { ActivityInfoRef(it.id, it.name) }, organizationName,
            program?.let { ActivityInfoRef(it.id, it.name) }, programName,
            activity?.let { ActivityInfoRef(it.id, it.name) }, activityName,
            role?.let { ActivityInfoRef(it.id, it.name) }, roleName,
            startDate, endDate, description,
            ApprovalStatus.valueOf(approval), approvalReason, VerificationStatus.valueOf(verification),
            evidence.map { EvidenceMeta(it.id, it.fileName, it.contentType, it.size, it.uploadedAt) },
        )

        companion object {
            fun from(h: History) = HistoryJson(
                h.id, h.kind.name,
                h.organization?.let { RefJson(it.id, it.name) }, h.organizationName,
                h.program?.let { RefJson(it.id, it.name) }, h.programName,
                h.activity?.let { RefJson(it.id, it.name) }, h.activityName,
                h.role?.let { RefJson(it.id, it.name) }, h.roleName,
                h.startDate, h.endDate, h.description,
                h.approval.name, h.approvalReason, h.verification.name,
                h.evidence.map { EvidenceJson(it.id, it.fileName, it.contentType, it.size, it.uploadedAt) },
            )
        }
    }
}
