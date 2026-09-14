import Foundation

@main
struct NoticeDetailTests {
    @MainActor
    static func main() throws {
        let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
        let catalog = try JSONDecoder().decode(NoticeCatalog.self, from: data)
        let originalJSON = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        let originalNotices = originalJSON["activities"] as! [[String: Any]]
        try directConstruction(originalNotices.first { $0["favoriteOrganizationId"] as? String == "yeongnam-cyber-defense" }!)
        let source = DetailCountingSource(catalog.organizations)
        let repository = NoticeDetailRepository(catalog: catalog, source: source)
        precondition(source.fetches.isEmpty)
        let contest = catalog.notices.first { $0.favoriteOrganizationId == "yeongnam-cyber-defense" }!
        let first = repository.detail(id: contest.id)!
        let coldFetches = source.fetches
        precondition(coldFetches["yeongnam-cyber-defense"] == 1 && coldFetches["yeongnam-ai-security"] == 1)
        let reopened = repository.detail(id: contest.id)!
        precondition(source.fetches == coldFetches, "Two opens share the same source/cache lifetime")
        precondition(first.organizationID == "yeongnam-cyber-defense" && reopened.organizationPath.map(\.id) == catalog.organizationPath(contest.favoriteOrganizationId).map(\.id))
        precondition(first.aiDescription == contest.summary && first.descriptionProvenance == "reviewed_sample.summary")
        precondition(first.targetUser == contest.audience && first.participationCondition == contest.eligibility)
        precondition(first.applicationInformation.summary == contest.application.summary)
        precondition(first.schedules.first { $0.period.phase == "preliminary" }!.locations.isEmpty)
        precondition(first.schedules.first { $0.period.phase == "final" }!.locations.map(\.name) == contest.location.venues.map(\.name))
        precondition(first.organizationLinks.map { $0.reference.role } == ["publisher", "contact", "subject"])
        precondition(repository.detail(id: "missing") == nil)
        for notice in catalog.notices {
            let detail = repository.detail(id: notice.id)!
            for link in detail.organizationLinks {
                precondition(link.organizationName == catalog.organization(link.reference.organizationId)?.name && link.organizationName != nil)
                let original = notice.organizationLinks.first { $0.organizationId == link.reference.organizationId && $0.role == link.reference.role }!
                precondition(link.reference.basis == original.basis && link.reference.note == original.note)
                precondition(source.fetches[link.reference.organizationId] == 1, "Links, contexts and paths share successful cache entries")
            }
            precondition(detail.title == notice.title && detail.categorySummary == notice.categorySummary)
            precondition(detail.location.summary == notice.location.summary && detail.benefits == notice.benefits && detail.qualityIssues == notice.qualityIssues)
            precondition(detail.contexts.map { $0.reference.organizationId } == notice.contexts.map(\.organizationId))
            precondition(detail.contexts.map { $0.reference.label } == notice.contexts.map(\.label))
            let rawNotice = originalNotices.first { $0["id"] as? String == notice.id }!
            precondition(detail.evidence.count == evidenceCount(rawNotice) && !detail.evidence.isEmpty)
            for evidence in detail.evidence {
                precondition(!evidence.fieldPath.isEmpty && !evidence.locator.isEmpty)
                precondition(evidence.sourceURL?.absoluteString == catalog.sources.first { $0.id == evidence.sourceId }?.url)
            }
            precondition(detail.sources.allSatisfy { $0.kind != nil && $0.checkedAt != nil })
        }
        let krc = catalog.notices.first { $0.favoriteOrganizationId == "krc" }!
        let detail = repository.detail(id: krc.id)!
        precondition(detail.contexts[0].reference.role == "event_context" && detail.contexts[0].organizationName == "충북대학교")
        precondition(detail.contexts[0].reference.basis == "user_confirmed")
        precondition(detail.applicationInformation.channels == ["platform"])
        precondition(detail.evidence.contains { $0.fieldPath == "schedule.duration" && $0.sourceId == "krc" })
        if krc.location.venues[0].coordinates != nil {
            precondition(detail.evidence.contains { $0.fieldPath == "location.venues[0].coordinates" && $0.sourceURL != nil })
        }
        // IDs stay normalized in the saved payload; selecting a parent is allowed.
        var raw = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        var notices = raw["activities"] as! [[String: Any]]
        let index = notices.firstIndex { $0["id"] as? String == krc.id }!
        precondition(notices[index]["favoriteOrganizationId"] is String && notices[index]["organizationPath"] == nil)
        notices[index]["favoriteOrganizationId"] = "cbnu"
        notices[index]["organizationLinks"] = [
            ["organizationId": "unknown-link", "role": "contact", "basis": "source", "note": "보존"],
            ["organizationId": "cbnu", "role": "publisher"]
        ]
        notices[index]["contexts"] = [["organizationId": "unknown", "role": "venue_institution"], ["organizationId": "cbnu", "role": "event_context"]]
        var audience = notices[index]["audience"] as! [String: Any]
        audience["evidence"] = [["sourceId": "missing-source", "locator": "보존해야 함"]]
        notices[index]["audience"] = audience
        raw["activities"] = notices
        var organizations = raw["organizations"] as! [[String: Any]]
        let parentIndex = organizations.firstIndex { $0["id"] as? String == "cbnu" }!
        organizations[parentIndex]["name"] = "바뀐 학교"
        organizations[parentIndex]["parentOrganizationId"] = "unknown-parent"
        raw["organizations"] = organizations
        let changed = try JSONDecoder().decode(NoticeCatalog.self, from: JSONSerialization.data(withJSONObject: raw))
        let changedSource = DetailCountingSource(changed.organizations)
        repository.replaceSnapshot(changed, source: changedSource)
        precondition(changedSource.fetches.isEmpty, "Replacement clears rather than prewarms")
        let updated = repository.detail(id: krc.id)!
        precondition(updated.organizationLinks[0].reference.organizationId == "unknown-link" && updated.organizationLinks[0].reference.role == "contact")
        precondition(updated.organizationLinks[0].reference.basis == "source" && updated.organizationLinks[0].reference.note == "보존" && updated.organizationLinks[0].organizationName == nil)
        precondition(updated.organizationLinks[1].organizationName == "바뀐 학교" && changedSource.fetches["cbnu"] == 1)
        precondition(updated.organizationID == "cbnu" && updated.organizationPath.map(\.name) == ["바뀐 학교"])
        precondition(updated.contexts[0].organizationName == nil && updated.contexts[0].reference.label == "개최 기관")
        precondition(updated.evidence.contains { $0.sourceId == "missing-source" && $0.locator == "보존해야 함" && $0.fieldPath == "audience" && $0.sourceURL == nil })
        precondition(repository.catalog.organization("cbnu")?.name == updated.organizationPath.last?.name)
        print("PASS: shared repository two detail opens, ID-only references/selected parent, exact contexts and phase places, decoded sources/evidence paths/unknown source, snapshot rename/reparent replacement")
    }
    // No repository or catalog: construction consumes only decoded notice and supplied values.
    private static func directConstruction(_ original: [String: Any]) throws {
        var raw = original
        raw["sourceIds"] = ["primary", "secondary"]
        raw["evidence"] = [["sourceId": "secondary", "locator": "보조 근거"], ["sourceId": "missing", "locator": "미해결"]]
        var location = raw["location"] as! [String: Any]
        location["venues"] = [
            ["phase": "preliminary", "name": "온라인에 넣지 않을 장소"],
            ["phase": "final", "name": "첫 결선 장소"],
            ["phase": "final", "name": "두 번째 결선 장소"],
            ["phase": "unmatched", "name": "다른 단계"]
        ]
        raw["location"] = location
        let notice = try JSONDecoder().decode(Notice.self, from: JSONSerialization.data(withJSONObject: raw))
        let context = NoticeDetailContext(reference: NoticeContext(organizationId: "selected", role: "event_context"), organizationName: "주입 이름")
        let primary = NoticeSource(id: "primary", url: "https://example.com/primary")
        let secondary = NoticeSource(id: "secondary", url: "https://example.com/secondary")
        let detail = NoticeDetail(notice: notice, organizationPath: [], contexts: [context], organizationLinks: [context], sources: [secondary, primary])
        precondition(detail.sourceURL?.absoluteString == primary.url, "Use notice first source ID, never array order")
        let unmatched = NoticeDetail(notice: notice, organizationPath: [], contexts: [], organizationLinks: [], sources: [secondary])
        precondition(unmatched.sourceURL == nil, "Missing primary must not fall back to another source")
        precondition(detail.evidence.contains { $0.sourceId == "secondary" && $0.sourceURL?.absoluteString == secondary.url })
        precondition(detail.evidence.contains { $0.sourceId == "missing" && $0.sourceURL == nil })
        precondition(detail.schedules.first { $0.period.phase == "preliminary" }!.locations.isEmpty)
        precondition(detail.schedules.first { $0.period.phase == "final" }!.locations.map(\.name) == ["첫 결선 장소", "두 번째 결선 장소"])
        precondition(detail.title == notice.title && detail.aiDescription == notice.summary && detail.descriptionProvenance == "reviewed_sample.summary")
        precondition(detail.organizationID == notice.favoriteOrganizationId && detail.contexts[0].organizationName == "주입 이름" && detail.organizationLinks[0].reference.role == "event_context")
        precondition(detail.applicationInformation.summary == notice.application.summary && detail.targetUser == notice.audience && detail.participationCondition == notice.eligibility)
        precondition(detail.categoryPath == notice.categoryPath && detail.benefits == notice.benefits && detail.qualityIssues == notice.qualityIssues && detail.edition == notice.edition)
        print("PASS: value-only detail initializer, misordered/missing primary source, evidence resolution, exact phase/online venues, preserved fields")
    }

    private static func evidenceCount(_ value: Any) -> Int {
        if let object = value as? [String: Any] {
            return object.reduce(0) { count, entry in
                if entry.key == "evidence" || entry.key == "coordinateEvidence" {
                    return count + (entry.value as! [Any]).count
                }
                return count + evidenceCount(entry.value)
            }
        }
        return (value as? [Any])?.reduce(0) { $0 + evidenceCount($1) } ?? 0
    }
}

@MainActor
private final class DetailCountingSource: OrganizationSource {
    private let records: [String: NoticeOrganization]
    private(set) var fetches: [String: Int] = [:]
    init(_ records: [NoticeOrganization]) { self.records = Dictionary(uniqueKeysWithValues: records.map { ($0.id, $0) }) }
    func fetch(id: String) -> NoticeOrganization? { fetches[id, default: 0] += 1; return records[id] }
}
