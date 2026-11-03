package io.wid.dearby.application.profile

import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.ConflictException
import io.wid.dearby.domain.ContactKind
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.PermissionDeniedException
import io.wid.dearby.domain.profile.ConsentVersions
import io.wid.dearby.domain.profile.ConsentStatus
import io.wid.dearby.domain.profile.Consent
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
    private val photos: ProfilePhotoRepository,
    private val reviews: HistoryReviewRepository,
    private val organizations: OrganizationRepository,
    private val programs: ProgramRepository,
    private val activities: ActivityRepository,
    private val roles: RoleRepository,
    private val jobs: JobRepository,
    private val consents: ConsentRepository,
    private val consentVersions: ConsentVersions,
) {

    @Transactional(readOnly = true)
    fun get(userId: UserId): Profile = profiles.find(userId) ?: empty(userId)

    // 전체를 바꾼다. 바뀐 것이 없으면 버전을 올리지 않는다. ifMatch가 현재 버전과 다르면 409
    @Transactional
    fun update(userId: UserId, input: ProfileInput, ifMatch: Int?): Profile {
        requireConsent(userId)
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
        requireConsent(userId)
        val snapshot = version(userId, version).profile
        val input = ProfileInput(
            name = snapshot.name,
            jobs = snapshot.jobs.map { JobInput(it.id, it.job?.id, it.jobName) },
            introLink = snapshot.introLink,
            contacts = snapshot.contacts.map { ContactInput(it.id, it.kind.name.lowercase(), it.label, it.value) },
            histories = snapshot.histories.map {
                HistoryInput(it.id, it.role?.id, it.owner.kind.name.lowercase(), it.owner.id, it.owner.name, it.roleName, it.startDate, it.endDate, it.description)
            },
        )
        return update(userId, input, null)
    }

    @Transactional
    fun addEvidence(userId: UserId, historyId: UUID, fileName: String, contentType: String, data: ByteArray): EvidenceMeta {
        requireConsent(userId)
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
        requireConsent(userId)
        val history = requireHistory(userId, historyId)
        if (!evidence.delete(historyId, evidenceId)) throw NotFoundException("인증자료를 찾을 수 없습니다", "Evidence not found: $evidenceId")
        if (history.verification != VerificationStatus.VERIFIED && evidence.count(historyId) == 0) {
            reviews.updateVerification(historyId, VerificationStatus.UNVERIFIED)
        }
    }

    // 프로필 사진(native-v1.md "프로필 사진"): 앱이 정사각형으로 자른 JPEG·PNG를 서버가 다시 인코딩해 둔다. 버전 스냅샷과 무관하다
    @Transactional
    fun putPhoto(userId: UserId, contentType: String, data: ByteArray): OffsetDateTime {
        requireConsent(userId)
        if (contentType !in PHOTO_TYPES) throw InvalidInputException("사진은 JPEG·PNG만 올릴 수 있습니다")
        if (data.isEmpty() || data.size > MAX_PHOTO_BYTES) throw InvalidInputException("사진은 5MB까지입니다")
        val encoded = ProfilePhotoImage.reencode(data)
        // DB(timestamptz)는 마이크로초까지라 photoUrl의 버전도 저장값과 같게 자른다
        val now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        photos.save(userId, encoded, now)
        return now
    }

    @Transactional(readOnly = true)
    fun photo(userId: UserId): ByteArray = photos.find(userId) ?: throw NotFoundException("프로필 사진이 없습니다", "Profile photo not found: $userId")

    @Transactional(readOnly = true)
    fun photoUpdatedAt(userId: UserId): OffsetDateTime? = photos.updatedAt(userId)

    // 없어도 204(한 칸짜리라 지운 상태가 목표)
    @Transactional
    fun deletePhoto(userId: UserId) {
        requireConsent(userId)
        photos.delete(userId)
    }

    // 개인정보 동의(native-v1.md "개인정보 동의"): 기록이 없거나 버전이 다르면 프로필 쓰기를 막는다
    @Transactional(readOnly = true)
    fun consent(userId: UserId): ConsentStatus {
        val consent = consents.find(userId)
        return ConsentStatus(consent, !consentVersions.satisfiedBy(consent))
    }

    @Transactional
    fun giveConsent(userId: UserId, privacyVersion: String, termsVersion: String) {
        if (privacyVersion != consentVersions.privacy || termsVersion != consentVersions.terms) {
            throw InvalidInputException("동의 문서 버전이 바뀌었어요. 앱을 업데이트한 뒤 다시 동의해 주세요.", "Consent version mismatch: $privacyVersion/$termsVersion", code = CONSENT_VERSION)
        }
        consents.save(userId, Consent(privacyVersion, termsVersion, OffsetDateTime.now(ZoneOffset.UTC)))
    }

    // 광고 목적 제3자 제공 동의(선택, native-v1.md "광고 목적 개인정보 제3자 제공 동의(선택)"): 프로필 동의와 별개이고 프로필 쓰기를 막지 않는다
    @Transactional(readOnly = true)
    fun adConsent(userId: UserId): AdConsent? = consents.findAd(userId)

    // 현재 버전이 아니면 400 CONSENT_VERSION. 같은 버전에 다시 동의하면 처음 동의 시각을 지킨다
    @Transactional
    fun giveAdConsent(userId: UserId, version: String): AdConsent {
        if (version != AdConsent.VERSION) {
            throw InvalidInputException("광고 목적 개인정보 제3자 제공 동의 버전이 현재 버전과 다릅니다", "Ad consent version mismatch: $version", code = CONSENT_VERSION)
        }
        consents.findAd(userId)?.takeIf { it.version == version }?.let { return it }
        // DB(timestamptz)는 마이크로초까지라 응답도 저장값과 같게 자른다
        return AdConsent(version, OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS)).also { consents.saveAd(userId, it) }
    }

    @Transactional
    fun withdrawAdConsent(userId: UserId) = consents.deleteAd(userId)

    // 프로필 쓰기 전 동의 확인(연락처 인증 서비스도 쓴다)
    fun requireConsent(userId: UserId) {
        if (!consentVersions.satisfiedBy(consents.find(userId))) {
            throw PermissionDeniedException("개인정보 수집·이용과 이용약관에 먼저 동의해 주세요", "Consent required: $userId", code = CONSENT_REQUIRED)
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
        val introLink = introLink(input.introLink)
        val existingContacts = current.contacts.associateBy { it.id }
        val contacts = input.contacts.map { contact(it, existingContacts[it.id]) }
        if (contacts.map { it.id }.toSet().size != contacts.size) throw InvalidInputException("연락처 id가 겹칩니다")
        if (contacts.none { it.kind == ContactKind.PHONE } || contacts.none { it.kind == ContactKind.EMAIL }) {
            throw InvalidInputException("전화번호와 이메일을 하나 이상 넣어 주세요")
        }
        if (input.histories.map { it.id }.toSet().size != input.histories.size) throw InvalidInputException("이력 id가 겹칩니다")
        val existing = current.histories.associateBy { it.id }
        val histories = input.histories.map { history(it, existing[it.id]) }
        return Profile(userId, name, jobEntries, introLink, contacts, histories, current.version, current.updatedAt)
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

    // 사용자명 종류(GitHub·Instagram·Behance·카카오톡)는 사용자명만 저장한다(native-v1.md "연락처 사용자명 입력", 2026-10-26). 프로필 URL이 오면 사용자명으로 바꾸고(옛 앱 호환),
    // 형식이 틀리면 400. 이미 저장된 그대로의 값(2026-10-26 전 오픈채팅 URL 등)은 다시 검사하지 않는다 — 변경 없는 저장이 막히지 않도록
    private fun contact(input: ContactInput, existing: Contact?): Contact {
        val kind = ContactKind.entries.firstOrNull { it.name.equals(input.kind, ignoreCase = true) }
            ?: throw InvalidInputException("연락처 종류가 올바르지 않습니다: ${input.kind}")
        val label = text(input.label, "연락처 이름", MAX_NAME, allowEmpty = true)
        val value = text(input.value, "연락처 값", MAX_VALUE)
        val stored = when (kind) {
            ContactKind.PHONE -> value.also { if (!PHONE.matches(it) || it.count { c -> c.isDigit() } !in 8..15) throw InvalidInputException("전화번호 형식이 올바르지 않습니다") }
            ContactKind.EMAIL -> value.also { if (!EMAIL.matches(it)) throw InvalidInputException("이메일 형식이 올바르지 않습니다") }
            else -> {
                val rule = USERNAME_RULES.getValue(kind)
                username(value, rule) ?: value.also {
                    if (existing?.kind != kind || existing.value != it) {
                        throw InvalidInputException("사용자명은 영문·숫자·.·_·-로 ${rule.max}자까지입니다", "Invalid ${kind.name.lowercase()} username")
                    }
                }
            }
        }
        // 인증 상태(native-v1.md "연락처 인증")는 클라이언트가 보내지 못한다. 종류·값이 그대로면 유지하고, 값을 고치면 다시 미인증
        val keep = existing != null && existing.verified && existing.kind == kind && sameValue(kind, existing.value, stored)
        return Contact(input.id, kind, label, stored, verified = keep, verifiedAt = existing?.verifiedAt.takeIf { keep })
    }

    // 인증 결과로 연락처를 켠다(native-v1.md "연락처 인증"): 같은 값의 항목이 있으면 그것을, 없으면 그 종류의 첫 항목 값을 바꾸고(수동 입력을 덮어쓴다), 그 종류가 없으면 끝에 더한다.
    // 프로필 저장과 같이 버전이 오른다(이미 같은 값으로 인증돼 있으면 그대로). 프로필이 아직 없으면 400 PROFILE_REQUIRED
    @Transactional
    fun markContactVerified(userId: UserId, kind: ContactKind, value: String): Profile {
        requireConsent(userId)
        val current = profiles.find(userId) ?: throw InvalidInputException("프로필을 먼저 저장해 주세요", "Profile missing for contact verification: $userId", code = PROFILE_REQUIRED)
        val index = current.contacts.indexOfFirst { it.kind == kind && sameValue(kind, it.value, value) }.takeIf { it >= 0 }
            ?: current.contacts.indexOfFirst { it.kind == kind }.takeIf { it >= 0 }
        if (index != null && current.contacts[index].verified && sameValue(kind, current.contacts[index].value, value)) return current
        // DB(timestamptz)는 마이크로초까지라 응답도 저장값과 같게 자른다
        val now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        val verified = Contact(UUID.randomUUID(), kind, "", value, verified = true, verifiedAt = now)
        val contacts = if (index == null) current.contacts + verified else current.contacts.mapIndexed { i, c -> if (i == index) verified.copy(id = c.id, label = c.label) else c }
        val saved = current.copy(contacts = contacts, version = current.version + 1, updatedAt = now)
        profiles.save(saved)
        versions.append(saved)
        return profiles.find(userId) ?: saved
    }

    // 이메일은 대소문자를 가리지 않는다
    private fun sameValue(kind: ContactKind, a: String, b: String) = if (kind == ContactKind.EMAIL) a.equals(b, ignoreCase = true) else a == b

    // `[https://][www.|m.]host/name[/…]`는 name, `@`·공백은 지운다. 남은 값이 허용 문자·길이를 넘으면 null
    private fun username(value: String, rule: UsernameRule): String? {
        val path = rule.host?.let { host -> Regex("^(https?://)?((www|m)\\.)?" + Regex.escape(host) + "/", RegexOption.IGNORE_CASE).find(value) }
            ?.let { value.substring(it.range.last + 1).takeWhile { c -> c !in "/?#" } } ?: value
        val id = path.filterNot { it.isWhitespace() || it == '@' }
        return id.takeIf { it.isNotEmpty() && it.length <= rule.max && USERNAME.matches(it) }
    }

    private class UsernameRule(val host: String?, val max: Int)

    // 소개 링크(native-v1.md "소개 링크·소개 글 폐지"): 비면 null, 스킴이 없으면 https://를 붙이고 https 주소(호스트에 점, 공백 없음)만 받는다
    private fun introLink(value: String?): String? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        val url = if (text.contains("://")) text else "https://$text"
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true) || uri.host?.contains('.') != true || url.any { it.isWhitespace() }) {
            throw InvalidInputException("소개 링크는 https 주소만 쓸 수 있습니다")
        }
        if (url.length > MAX_INTRO_LINK) throw InvalidInputException("소개 링크는 ${MAX_INTRO_LINK}자까지입니다")
        return url
    }

    // 역할 매칭(native-v1.md "이력 (History) — 역할 매칭"): roleId가 있으면 그 역할과 귀속 단위를 쓰고 직접 입력 값은 버린다.
    // 없으면 직접 입력(승인 대기): 등록된 단위(ownerKind·ownerId) 또는 단위 이름(ownerKind·ownerName) + 역할 이름. 상태는 기존 이력과 비교해 정한다
    private fun history(input: HistoryInput, existing: History?): History {
        val role = input.roleId?.let { id -> roles.findAllById(listOf(id)).singleOrNull() ?: throw InvalidInputException("역할을 찾을 수 없습니다", "Role not found: $id") }
        val units = if (role != null) {
            units(role.owner.kind, role.owner.id!!, publishedOnly = false) ?: throw InvalidInputException("역할의 귀속 단위를 찾을 수 없습니다", "Role owner not found: ${role.id}")
        } else {
            val kind = RoleOwnerKind.entries.firstOrNull { it.name.equals(input.ownerKind, ignoreCase = true) }
                ?: throw InvalidInputException("ownerKind는 organization·program·activity 중 하나입니다")
            if (input.ownerId != null) {
                units(kind, input.ownerId, publishedOnly = true) ?: throw InvalidInputException("${UNIT_LABELS.getValue(kind)}을(를) 찾을 수 없습니다", "${kind.name} not found: ${input.ownerId}")
            } else {
                val name = optional(input.ownerName, "${UNIT_LABELS.getValue(kind)} 이름", MAX_ACTIVITY_INFO_NAME) ?: throw InvalidInputException("조직·프로그램·활동을 고르거나 적어 주세요")
                Units(RoleOwner(kind, null, name), null, null, null)
            }
        }
        val roleName = role?.name ?: optional(input.roleName, "역할 이름", MAX_NAME) ?: throw InvalidInputException("역할을 고르거나 적어 주세요")
        // 7일 미만 행사는 이력이 아니다(native-v1.md "활동 이력에서 단기 행사 제외"). 직접 입력한 활동 이름은 제한하지 않고,
        // 이미 같은 활동으로 저장된 이력은 다시 검사하지 않는다(기존 이력·버전 되돌리기가 막히지 않도록, 2026-10-20 조율 결정)
        val activityId = units.activity?.id
        if (activityId != null && existing?.activity?.id != activityId && !activities.isHistoryEligible(activityId)) {
            throw InvalidInputException("일주일 미만 행사는 활동 이력에 넣을 수 없어요", "Activity too short for history: $activityId", code = HISTORY_ACTIVITY_TOO_SHORT)
        }
        if (input.endDate != null && input.endDate.isBefore(input.startDate)) throw InvalidInputException("종료일은 시작일보다 앞설 수 없습니다")
        val draft = History(
            id = input.id,
            role = role?.let { ActivityInfoRef(it.id, it.name) }, roleName = roleName,
            owner = units.owner, organization = units.organization, program = units.program, activity = units.activity,
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

    // 귀속 단위와 상위 단위(활동 → 프로그램 → 조직). 사용자가 직접 고르는 활동은 게시된 것만, 역할에서 따라오는 활동은 게시 상태와 무관(이미 저장된 이력이 막히지 않도록)
    private fun units(kind: RoleOwnerKind, id: Id, publishedOnly: Boolean): Units? = when (kind) {
        RoleOwnerKind.ACTIVITY -> activities.findById(id, PublicationStatus.PUBLISHED.takeIf { publishedOnly })?.let { a ->
            val program = programs.findAllById(listOf(a.programId)).singleOrNull()
            val organization = program?.let { organizations.findAllById(listOf(it.organizationId)).singleOrNull() }
            Units(RoleOwner(kind, a.id, a.name), organization?.let { ActivityInfoRef(it.id, it.name) }, program?.let { ActivityInfoRef(it.id, it.name) }, ActivityInfoRef(a.id, a.name))
        }
        RoleOwnerKind.PROGRAM -> programs.findAllById(listOf(id)).singleOrNull()?.let { p ->
            val organization = organizations.findAllById(listOf(p.organizationId)).singleOrNull()
            Units(RoleOwner(kind, p.id, p.name), organization?.let { ActivityInfoRef(it.id, it.name) }, ActivityInfoRef(p.id, p.name), null)
        }
        RoleOwnerKind.ORGANIZATION -> organizations.findAllById(listOf(id)).singleOrNull()?.let { o ->
            Units(RoleOwner(kind, o.id, o.name), ActivityInfoRef(o.id, o.name), null, null)
        }
    }

    private class Units(val owner: RoleOwner, val organization: ActivityInfoRef?, val program: ActivityInfoRef?, val activity: ActivityInfoRef?)

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
        const val CONSENT_REQUIRED = "CONSENT_REQUIRED"
        const val CONSENT_VERSION = "CONSENT_VERSION"
        const val HISTORY_ACTIVITY_TOO_SHORT = "HISTORY_ACTIVITY_TOO_SHORT"
        const val PROFILE_REQUIRED = "PROFILE_REQUIRED"
        const val MAX_PAGE_SIZE = 100
        const val MAX_EVIDENCE_PER_HISTORY = 3
        const val MAX_JOBS = 10
        const val MAX_EVIDENCE_BYTES = 5 * 1024 * 1024
        const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
        val EVIDENCE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "application/pdf")
        val PHOTO_TYPES = setOf("image/jpeg", "image/png")
        private const val MAX_NAME = 100
        private const val MAX_VALUE = 500
        private const val MAX_ACTIVITY_INFO_NAME = 200
        private const val MAX_INTRO_LINK = 500
        private const val MAX_DESCRIPTION = 10_000
        private val UNIT_LABELS = mapOf(RoleOwnerKind.ORGANIZATION to "조직", RoleOwnerKind.PROGRAM to "프로그램", RoleOwnerKind.ACTIVITY to "활동")
        private val PHONE = Regex("^[0-9+\\- ]+$")
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        // 앱(iOS ContactModel.usernameRules, Android ContactRules)과 같은 표. 길이는 GitHub 39·Instagram 30(각 서비스 규칙), Behance 64·카카오톡 20은 확인 못 해 넉넉히
        private val USERNAME_RULES = mapOf(
            ContactKind.GITHUB to UsernameRule("github.com", 39), ContactKind.INSTAGRAM to UsernameRule("instagram.com", 30),
            ContactKind.BEHANCE to UsernameRule("behance.net", 64), ContactKind.KAKAO to UsernameRule(null, 20),
        )
        private val USERNAME = Regex("^[A-Za-z0-9._-]+$")
        private val EPOCH: OffsetDateTime = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    }
}
