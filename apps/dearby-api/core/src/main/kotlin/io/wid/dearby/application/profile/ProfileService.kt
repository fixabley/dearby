package io.wid.dearby.application.profile

import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.ConflictException
import io.wid.dearby.domain.ContactKind
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.PublicationStatus
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 내 프로필(native-v1.md "프로필·명함 v2"): 데이터 입력, 저장마다 버전, 이력의 승인·인증 상태, 인증자료
@Service
class ProfileService(
    private val profiles: ProfileRepository,
    private val versions: ProfileVersionRepository,
    private val evidence: EvidenceRepository,
    private val reviews: HistoryReviewRepository,
    private val organizations: OrganizationRepository,
    private val programs: ProgramRepository,
    private val activities: ActivityRepository,
    private val roles: RoleRepository,
    private val jobs: JobRepository,
) {

    @Transactional(readOnly = true)
    fun get(userId: UserId): Profile = profiles.find(userId) ?: empty(userId)

    // 전체를 바꾼다. 바뀐 것이 없으면 버전을 올리지 않는다. ifMatch가 현재 버전과 다르면 409
    @Transactional
    fun update(userId: UserId, input: ProfileInput, ifMatch: Int?): Profile {
        val current = get(userId)
        if (ifMatch != null && ifMatch != current.version) throw ConflictException("프로필이 다른 곳에서 바뀌었습니다. 다시 불러와 주세요", "If-Match $ifMatch != ${current.version}")
        val next = build(userId, input, current)
        if (same(current, next)) return current
        val saved = next.copy(version = current.version + 1, updatedAt = OffsetDateTime.now(ZoneOffset.UTC))
        profiles.save(saved)
        versions.append(saved)
        return profiles.find(userId) ?: saved
    }

    @Transactional(readOnly = true)
    fun versions(userId: UserId, after: Int?, size: Int): Pair<List<Pair<Int, OffsetDateTime>>, Int?> {
        if (size !in 1..MAX_PAGE_SIZE) throw InvalidInputException("size는 1에서 ${MAX_PAGE_SIZE} 사이여야 합니다")
        val found = versions.list(userId, after, size + 1)
        val page = found.take(size)
        return page to (if (found.size > size) page.last().first else null)
    }

    @Transactional(readOnly = true)
    fun version(userId: UserId, version: Int): ProfileVersion =
        versions.find(userId, version) ?: throw NotFoundException("그 버전이 없습니다", "Profile version not found: $userId/$version")

    // 그 버전의 내용으로 새 버전을 만든다. 상태는 지금 기준으로 다시 정한다
    @Transactional
    fun restore(userId: UserId, version: Int): Profile {
        val snapshot = version(userId, version).profile
        val input = ProfileInput(
            name = snapshot.name,
            jobs = snapshot.jobs.map { JobInput(it.id, it.job?.id, it.jobName) },
            introduction = snapshot.introduction,
            contacts = snapshot.contacts.map { ContactInput(it.id, it.kind.name.lowercase(), it.label, it.value) },
            histories = snapshot.histories.map {
                HistoryInput(
                    it.id, it.kind.name.lowercase(), it.organization?.id, it.organizationName, it.program?.id, it.programName,
                    it.activity?.id, it.activityName, it.role?.id, it.roleName, it.startDate, it.endDate, it.description,
                )
            },
        )
        return update(userId, input, null)
    }

    @Transactional
    fun addEvidence(userId: UserId, historyId: UUID, fileName: String, contentType: String, data: ByteArray): EvidenceMeta {
        val history = requireHistory(userId, historyId)
        if (contentType !in EVIDENCE_TYPES) throw InvalidInputException("이미지(JPEG·PNG·WebP)나 PDF만 올릴 수 있습니다")
        if (data.isEmpty() || data.size > MAX_EVIDENCE_BYTES) throw InvalidInputException("파일은 5MB까지입니다")
        if (evidence.count(historyId) >= MAX_EVIDENCE_PER_HISTORY) throw InvalidInputException("인증자료는 이력당 ${MAX_EVIDENCE_PER_HISTORY}개까지입니다")
        val meta = EvidenceMeta(UUID.randomUUID(), fileName.ifBlank { "evidence" }.take(255), contentType, data.size, OffsetDateTime.now(ZoneOffset.UTC))
        evidence.add(historyId, userId, Evidence(meta, data))
        if (history.verification != VerificationStatus.VERIFIED) reviews.updateVerification(historyId, VerificationStatus.PENDING)
        return meta
    }

    @Transactional(readOnly = true)
    fun evidence(userId: UserId, historyId: UUID, evidenceId: UUID): Evidence {
        requireHistory(userId, historyId)
        return evidence.find(historyId, evidenceId) ?: throw NotFoundException("인증자료를 찾을 수 없습니다", "Evidence not found: $evidenceId")
    }

    // 확인된 이력은 자료를 지워도 그대로. 아니면 남은 자료가 없을 때 미인증으로 돌아간다
    @Transactional
    fun deleteEvidence(userId: UserId, historyId: UUID, evidenceId: UUID) {
        val history = requireHistory(userId, historyId)
        if (!evidence.delete(historyId, evidenceId)) throw NotFoundException("인증자료를 찾을 수 없습니다", "Evidence not found: $evidenceId")
        if (history.verification != VerificationStatus.VERIFIED && evidence.count(historyId) == 0) {
            reviews.updateVerification(historyId, VerificationStatus.UNVERIFIED)
        }
    }

    private fun requireHistory(userId: UserId, historyId: UUID): History =
        profiles.findHistory(userId, historyId) ?: throw NotFoundException("이력을 찾을 수 없습니다", "History not found: $userId/$historyId")

    private fun empty(userId: UserId) = Profile(userId, "", emptyList(), "", emptyList(), emptyList(), 0, OffsetDateTime.now(ZoneOffset.UTC))

    private fun build(userId: UserId, input: ProfileInput, current: Profile): Profile {
        val name = text(input.name, "이름", MAX_NAME)
        if (input.jobs.size > MAX_JOBS) throw InvalidInputException("직무는 ${MAX_JOBS}개까지입니다")
        if (input.jobs.map { it.id }.toSet().size != input.jobs.size) throw InvalidInputException("직무 id가 겹칩니다")
        val jobIds = input.jobs.mapNotNull { it.jobId }
        if (jobIds.toSet().size != jobIds.size) throw InvalidInputException("같은 직무를 두 번 넣었습니다")
        val foundJobs = jobs.findAllById(jobIds).associateBy { it.id }
        val existingJobs = current.jobs.associateBy { it.id }
        val jobEntries = input.jobs.map { job(it, foundJobs, existingJobs[it.id]) }
        val introduction = multiline(input.introduction, "자기소개", MAX_INTRODUCTION)
        val contacts = input.contacts.map(::contact)
        if (contacts.map { it.id }.toSet().size != contacts.size) throw InvalidInputException("연락처 id가 겹칩니다")
        if (contacts.none { it.kind == ContactKind.PHONE } || contacts.none { it.kind == ContactKind.EMAIL }) {
            throw InvalidInputException("전화번호와 이메일을 하나 이상 넣어 주세요")
        }
        if (input.histories.map { it.id }.toSet().size != input.histories.size) throw InvalidInputException("이력 id가 겹칩니다")
        val existing = current.histories.associateBy { it.id }
        val histories = input.histories.map { history(it, existing[it.id]) }
        return Profile(userId, name, jobEntries, introduction, contacts, histories, current.version, current.updatedAt)
    }

    // 직무 id가 있으면 이름을 직무에서 가져오고, 없으면 직접 입력(승인 대기). 직접 입력이 그대로면 승인·거절 결과를 지킨다
    private fun job(input: JobInput, found: Map<Id, Job>, existing: ProfileJob?): ProfileJob {
        val ref = input.jobId?.let { id -> found[id]?.let { ActivityInfoRef(it.id, it.name) } ?: throw InvalidInputException("직무를 찾을 수 없습니다", "Job not found: $id") }
        val typed = if (ref == null) optional(input.jobName, "직무 이름", MAX_NAME) ?: throw InvalidInputException("직무를 고르거나 적어 주세요") else null
        val draft = ProfileJob(input.id, ref, typed ?: ref?.name, ApprovalStatus.APPROVED, null)
        if (ref != null) return draft
        return if (existing != null && existing.customEntry == draft.customEntry) {
            draft.copy(approval = existing.approval, approvalReason = existing.approvalReason)
        } else {
            draft.copy(approval = ApprovalStatus.PENDING)
        }
    }

    private fun contact(input: ContactInput): Contact {
        val kind = ContactKind.entries.firstOrNull { it.name.equals(input.kind, ignoreCase = true) }
            ?: throw InvalidInputException("연락처 종류가 올바르지 않습니다: ${input.kind}")
        val label = text(input.label, "연락처 이름", MAX_NAME, allowEmpty = true)
        val value = text(input.value, "연락처 값", MAX_VALUE)
        when (kind) {
            ContactKind.PHONE -> if (!PHONE.matches(value) || value.count { it.isDigit() } !in 8..15) throw InvalidInputException("전화번호 형식이 올바르지 않습니다")
            ContactKind.EMAIL -> if (!EMAIL.matches(value)) throw InvalidInputException("이메일 형식이 올바르지 않습니다")
            else -> if (value.contains("://") && !value.startsWith("https://")) throw InvalidInputException("링크는 https 주소만 쓸 수 있습니다")
        }
        return Contact(input.id, kind, label, value)
    }

    // 활동 정보 id가 있으면 이름을 활동 정보에서 가져오고 직접 입력 이름은 버린다. 상태는 기존 이력과 비교해 정한다
    private fun history(input: HistoryInput, existing: History?): History {
        val kind = HistoryKind.entries.firstOrNull { it.name.equals(input.kind, ignoreCase = true) }
            ?: throw InvalidInputException("이력 종류는 organization 또는 activity입니다")
        val organization = input.organizationId?.let { id ->
            organizations.findAllById(listOf(id)).singleOrNull()?.let { ActivityInfoRef(it.id, it.name) } ?: throw InvalidInputException("조직을 찾을 수 없습니다", "Organization not found: $id")
        }
        val program = input.programId?.let { id ->
            programs.findAllById(listOf(id)).singleOrNull()?.let { ActivityInfoRef(it.id, it.name) } ?: throw InvalidInputException("프로그램을 찾을 수 없습니다", "Program not found: $id")
        }
        val activity = input.activityId?.let { id ->
            activities.findById(id, PublicationStatus.PUBLISHED)?.let { ActivityInfoRef(it.id, it.name) } ?: throw InvalidInputException("활동을 찾을 수 없습니다", "Activity not found: $id")
        }
        val role = input.roleId?.let { id ->
            roles.findAllById(listOf(id)).singleOrNull()?.let { ActivityInfoRef(it.id, it.name) } ?: throw InvalidInputException("역할을 찾을 수 없습니다", "Role not found: $id")
        }
        val organizationName = if (organization == null) optional(input.organizationName, "조직 이름", MAX_ACTIVITY_INFO_NAME) else null
        val programName = if (program == null) optional(input.programName, "프로그램 이름", MAX_ACTIVITY_INFO_NAME) else null
        val activityName = if (activity == null) optional(input.activityName, "활동 이름", MAX_ACTIVITY_INFO_NAME) else null
        val roleName = if (role == null) optional(input.roleName, "역할 이름", MAX_NAME) else null
        if (organization == null && organizationName == null) throw InvalidInputException("조직을 고르거나 적어 주세요")
        if (role == null && roleName == null) throw InvalidInputException("역할을 고르거나 적어 주세요")
        if (kind == HistoryKind.ACTIVITY && activity == null && activityName == null) throw InvalidInputException("활동을 고르거나 적어 주세요")
        if (input.endDate != null && input.endDate.isBefore(input.startDate)) throw InvalidInputException("종료일은 시작일보다 앞설 수 없습니다")
        val draft = History(
            id = input.id, kind = kind,
            organization = organization, organizationName = organizationName ?: organization?.name,
            program = program, programName = programName ?: program?.name,
            activity = activity, activityName = activityName ?: activity?.name,
            role = role, roleName = roleName ?: role?.name,
            startDate = input.startDate, endDate = input.endDate,
            description = multiline(input.description, "설명", MAX_DESCRIPTION),
            approval = ApprovalStatus.APPROVED, approvalReason = null,
            verification = existing?.verification ?: VerificationStatus.UNVERIFIED,
            evidence = existing?.evidence.orEmpty(),
        )
        if (!draft.hasCustomEntry) return draft
        // 직접 입력이 그대로면 승인·거절 결과를 지키고, 바뀌었으면 다시 승인 대기
        return if (existing != null && existing.customEntries == draft.customEntries) {
            draft.copy(approval = existing.approval, approvalReason = existing.approvalReason)
        } else {
            draft.copy(approval = ApprovalStatus.PENDING)
        }
    }

    // 내용만 비교한다(버전·시각·인증자료 목록 제외)
    private fun same(a: Profile, b: Profile): Boolean {
        fun strip(p: Profile) = p.copy(version = 0, updatedAt = EPOCH, histories = p.histories.map { it.copy(evidence = emptyList()) })
        return strip(a) == strip(b)
    }

    private fun text(value: String, field: String, max: Int, allowEmpty: Boolean = false): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty() && !allowEmpty) throw InvalidInputException("${field}을(를) 입력해 주세요")
        if (trimmed.length > max) throw InvalidInputException("${field}은(는) ${max}자까지입니다")
        if (trimmed.any { it.isISOControl() }) throw InvalidInputException("${field}에 쓸 수 없는 문자가 있습니다")
        return trimmed
    }

    private fun optional(value: String?, field: String, max: Int): String? = value?.trim()?.ifEmpty { null }?.let { text(it, field, max) }

    private fun multiline(value: String, field: String, max: Int): String {
        val trimmed = value.trim()
        if (trimmed.length > max) throw InvalidInputException("${field}은(는) ${max}자까지입니다")
        if (trimmed.any { it.isISOControl() && it != '\n' && it != '\t' && it != '\r' }) throw InvalidInputException("${field}에 쓸 수 없는 문자가 있습니다")
        return trimmed
    }

    companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_EVIDENCE_PER_HISTORY = 3
        const val MAX_JOBS = 10
        const val MAX_EVIDENCE_BYTES = 5 * 1024 * 1024
        val EVIDENCE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "application/pdf")
        private const val MAX_NAME = 100
        private const val MAX_VALUE = 500
        private const val MAX_ACTIVITY_INFO_NAME = 200
        private const val MAX_INTRODUCTION = 10_000
        private const val MAX_DESCRIPTION = 10_000
        private val PHONE = Regex("^[0-9+\\- ]+$")
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val EPOCH: OffsetDateTime = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    }
}
