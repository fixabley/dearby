import Foundation

/// TokenPair from the passkey and refresh endpoints: only the two tokens. Stored only in the Keychain.
/// The user id and expiry live in the access token's claims; the app refreshes on 401 instead of by the clock.
struct TokenPair: Codable, Equatable, Sendable {
    let accessToken: String
    let refreshToken: String
}
/// Profile contact. IDs are client-generated UUIDs; the server keeps them as given.
struct AccountContact: Codable, Equatable, Identifiable, Sendable {
    let id: String
    var kind: String
    var label: String
    var value: String
}
/// An activity-info item (organization, program, activity or role) a history points at. Numeric ids are the activity's.
struct ActivityInfoRef: Codable, Equatable, Hashable, Sendable {
    let id: Int
    let name: String
}
/// An uploaded evidence file's metadata (contract "인증자료"); the bytes stay on the server.
struct EvidenceMeta: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let fileName: String
    let contentType: String
    let size: Int
    let uploadedAt: String
}
/// A step-by-step history (contract "이력"): each of organization/program/activity/role is a activity ref or a
/// free-typed name. Free-typed values wait for admin approval (`approval`); evidence drives `verification`.
/// Encoding produces the `PUT /v1/profile` shape (`organizationId` + `organizationName`, …), never the refs.
struct AccountHistory: Codable, Equatable, Identifiable, Sendable {
    let id: String
    var kind: String
    var organization: ActivityInfoRef?
    var organizationName: String?
    var program: ActivityInfoRef?
    var programName: String?
    var activity: ActivityInfoRef?
    var activityName: String?
    var role: ActivityInfoRef?
    var roleName: String?
    var startDate: String
    var endDate: String?
    var description: String
    var approval = "approved"
    var approvalReason: String?
    var verification = "unverified"
    var evidence: [EvidenceMeta] = []

    init(id: String, kind: String, organization: ActivityInfoRef? = nil, organizationName: String? = nil, program: ActivityInfoRef? = nil,
         programName: String? = nil, activity: ActivityInfoRef? = nil, activityName: String? = nil, role: ActivityInfoRef? = nil,
         roleName: String? = nil, startDate: String, endDate: String? = nil, description: String = "", approval: String = "approved",
         approvalReason: String? = nil, verification: String = "unverified", evidence: [EvidenceMeta] = []) {
        self.id = id; self.kind = kind; self.organization = organization; self.organizationName = organizationName
        self.program = program; self.programName = programName; self.activity = activity; self.activityName = activityName
        self.role = role; self.roleName = roleName; self.startDate = startDate; self.endDate = endDate
        self.description = description; self.approval = approval; self.approvalReason = approvalReason
        self.verification = verification; self.evidence = evidence
    }
    private enum CodingKeys: String, CodingKey {
        case id, kind, organization, organizationName, program, programName, activity, activityName, role, roleName
        case startDate, endDate, description, approval, approvalReason, verification, evidence
        case organizationId, programId, activityId, roleId
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        kind = try c.decode(String.self, forKey: .kind)
        organization = try c.decodeIfPresent(ActivityInfoRef.self, forKey: .organization)
        organizationName = try c.decodeIfPresent(String.self, forKey: .organizationName)
        program = try c.decodeIfPresent(ActivityInfoRef.self, forKey: .program)
        programName = try c.decodeIfPresent(String.self, forKey: .programName)
        activity = try c.decodeIfPresent(ActivityInfoRef.self, forKey: .activity)
        activityName = try c.decodeIfPresent(String.self, forKey: .activityName)
        role = try c.decodeIfPresent(ActivityInfoRef.self, forKey: .role)
        roleName = try c.decodeIfPresent(String.self, forKey: .roleName)
        startDate = try c.decode(String.self, forKey: .startDate)
        endDate = try c.decodeIfPresent(String.self, forKey: .endDate)
        description = try c.decodeIfPresent(String.self, forKey: .description) ?? ""
        approval = try c.decodeIfPresent(String.self, forKey: .approval) ?? "approved"
        approvalReason = try c.decodeIfPresent(String.self, forKey: .approvalReason)
        verification = try c.decodeIfPresent(String.self, forKey: .verification) ?? "unverified"
        evidence = try c.decodeIfPresent([EvidenceMeta].self, forKey: .evidence) ?? []
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(kind, forKey: .kind)
        try c.encodeIfPresent(organization?.id, forKey: .organizationId)
        try c.encodeIfPresent(organization == nil ? organizationName : nil, forKey: .organizationName)
        try c.encodeIfPresent(program?.id, forKey: .programId)
        try c.encodeIfPresent(program == nil ? programName : nil, forKey: .programName)
        try c.encodeIfPresent(activity?.id, forKey: .activityId)
        try c.encodeIfPresent(activity == nil ? activityName : nil, forKey: .activityName)
        try c.encodeIfPresent(role?.id, forKey: .roleId)
        try c.encodeIfPresent(role == nil ? roleName : nil, forKey: .roleName)
        try c.encode(startDate, forKey: .startDate)
        try c.encode(endDate, forKey: .endDate) // null when ongoing
        try c.encode(description, forKey: .description)
    }
    /// 조직 → 프로그램 → 활동 names, activity or typed.
    var chain: [String] { [organization?.name ?? organizationName, program?.name ?? programName, activity?.name ?? activityName].compactMap { $0 } }
    var roleTitle: String { role?.name ?? roleName ?? "" }
    var isApproved: Bool { approval == "approved" }
    var isVerified: Bool { verification == "verified" }
    /// True when any part is free-typed (waits for, or was refused by, admin approval).
    var hasCustomEntry: Bool {
        (organization == nil && organizationName != nil) || (program == nil && programName != nil)
            || (activity == nil && activityName != nil) || (role == nil && roleName != nil)
    }
}
/// A 직무 tag (contract "직무"): picked from the job tree (`job`) or free-typed (`jobName` only), which waits for admin
/// approval before it reaches a card. Encodes as the PUT shape `{id, jobId?, jobName?}`.
struct ProfileJob: Codable, Equatable, Identifiable, Sendable {
    let id: String
    var job: ActivityInfoRef?
    var jobName: String?
    var approval = "approved"
    var approvalReason: String?
    var title: String { job?.name ?? jobName ?? "" }
    var isCustom: Bool { job == nil }
    var isApproved: Bool { approval == "approved" }
    private enum CodingKeys: String, CodingKey { case id, job, jobName, approval, approvalReason, jobId }
    init(id: String, job: ActivityInfoRef?, jobName: String?, approval: String = "approved", approvalReason: String? = nil) {
        self.id = id; self.job = job; self.jobName = jobName; self.approval = approval; self.approvalReason = approvalReason
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        job = try c.decodeIfPresent(ActivityInfoRef.self, forKey: .job)
        jobName = try c.decodeIfPresent(String.self, forKey: .jobName)
        approval = try c.decodeIfPresent(String.self, forKey: .approval) ?? "approved"
        approvalReason = try c.decodeIfPresent(String.self, forKey: .approvalReason)
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        if let job { try c.encode(job.id, forKey: .jobId) } else { try c.encodeIfPresent(jobName, forKey: .jobName) }
    }
}
/// `PUT /v1/profile` replaces the whole profile (data only; card decoration lives on cards). `version` comes from
/// `GET` and goes back as `If-Match`; it is never part of the body. `job` is the server's read-only join of approved job
/// names (what cards show); `jobs` is what the profile edits.
/// What the account agreed to (`GET /v1/profile.consent`, contract "개인정보 동의"); nil before the first consent.
struct ProfileConsent: Codable, Equatable, Sendable {
    let privacyVersion: String
    let termsVersion: String
    let consentedAt: String
}
struct AccountProfile: Codable, Equatable, Sendable {
    var name: String
    var job: String
    var jobs: [ProfileJob]
    var introduction: String
    var contacts: [AccountContact]
    var histories: [AccountHistory]
    var version = 0
    /// Read-only server state: the recorded consent and whether the current versions still need agreeing to
    /// (missing on older servers → false). Never sent back.
    var consent: ProfileConsent? = nil
    var consentRequired = false
    init(name: String, job: String = "", jobs: [ProfileJob] = [], introduction: String, contacts: [AccountContact], histories: [AccountHistory], version: Int = 0) {
        self.name = name; self.job = job; self.jobs = jobs; self.introduction = introduction; self.contacts = contacts; self.histories = histories; self.version = version
    }
    private enum CodingKeys: String, CodingKey { case name, job, jobs, introduction, contacts, histories, version, consent, consentRequired }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        job = try c.decodeIfPresent(String.self, forKey: .job) ?? ""
        jobs = try c.decodeIfPresent([ProfileJob].self, forKey: .jobs) ?? []
        introduction = try c.decodeIfPresent(String.self, forKey: .introduction) ?? ""
        contacts = try c.decodeIfPresent([AccountContact].self, forKey: .contacts) ?? []
        histories = try c.decodeIfPresent([AccountHistory].self, forKey: .histories) ?? []
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 0
        consent = try c.decodeIfPresent(ProfileConsent.self, forKey: .consent)
        consentRequired = try c.decodeIfPresent(Bool.self, forKey: .consentRequired) ?? false
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(name, forKey: .name)
        try c.encode(jobs, forKey: .jobs)
        try c.encode(introduction, forKey: .introduction)
        try c.encode(contacts, forKey: .contacts)
        try c.encode(histories, forKey: .histories)
    }
}
/// One saved profile version (`GET /v1/profile/versions`), newest first.
struct ProfileVersionSummary: Decodable, Equatable, Identifiable, Sendable {
    let version: Int
    let createdAt: String
    var id: Int { version }
}
struct ProfileVersionList: Decodable, Equatable, Sendable {
    let items: [ProfileVersionSummary]
    let nextCursor: Int?
}
/// `GET /v1/profile/versions/:n`: the profile as it was then.
struct ProfileVersionDetail: Decodable, Equatable, Sendable {
    let version: Int
    let createdAt: String
    let profile: AccountProfile
}
/// A activity search hit for the history pickers (`GET /v1/organizations|programs|roles|activities`).
struct ActivityInfoItem: Decodable, Equatable, Identifiable, Sendable {
    let id: Int
    let name: String
    var nameEn: String? = nil
    var parentOrganizationId: Int? = nil
    var organization: ActivityInfoRef? = nil
    /// Activities: the program the activity belongs to (contract "이력", 2026-10-18).
    var program: ActivityInfoRef? = nil
    /// Jobs: the job right above in the tree.
    var parent: ActivityInfoRef? = nil
    var ref: ActivityInfoRef { ActivityInfoRef(id: id, name: name) }
}
struct ActivityInfoPage: Decodable, Equatable, Sendable {
    let items: [ActivityInfoItem]
    let nextCursor: Int?
}
/// `GET /v1/wallet`: the cards saved to the account (contract "명함 교환" 2026-10-19).
struct Wallet: Decodable, Equatable, Sendable {
    struct Receipt: Decodable, Equatable, Identifiable, Sendable {
        let id: String
        let card: PublishedCard
        /// Where the card was exchanged; names are nil when the activity is gone.
        let context: ExchangeContext
        let receivedAt: String
        /// True when the card's owner holds one of mine too; such cards are never recalled.
        let reciprocal: Bool
        /// Server-computed recall time (received + 30 days); nil when reciprocal. Not recomputed on the device.
        let expiresAt: String?
    }
    let items: [Receipt]
}
struct ExchangeContext: Decodable, Equatable, Sendable {
    let activityId: Int64?
    let activityName: String?
    let programName: String?
}
/// A published card snapshot from `POST /v1/cards`. `introduction` is the filled-in template (markdown body);
/// `template` comes only on owner reads (the built-in template the card was published with).
struct PublishedCard: Decodable, Equatable, Identifiable, Sendable {
    let id: String
    let name: String
    let description: String
    let profileName: String
    let job: String
    var introduction: String? = nil
    var template: String? = nil
    let contacts: [AccountContact]
    let histories: [AccountHistory]
    let createdAt: String
}
