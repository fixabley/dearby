import Foundation

/// Versioned per-notice disk codec, independent of the richer bundle wire decoder.
enum NoticeStorageCodec {
    static func encode(_ value: NoticeModel) throws -> Data {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(Payload(value))
    }
    static func decode(_ data: Data) throws -> NoticeModel {
        let payload = try JSONDecoder().decode(Payload.self, from: data)
        guard payload.version == 1, payload.provenance == "reviewed_sample.summary" else { throw CocoaError(.coderReadCorrupt) }
        return payload.value
    }
    private struct Payload: Codable {
        let version: Int
        let provenance: String
        let id: String
        let title: String
        let aiDescription: String
        let demoVisible: Bool
        let favoriteOrganizationId: String?
        let sourceIds: [String]
        let targetUser: String
        let participationCondition: String
        let applicationInformation: NoticeApplication
        let location: NoticeLocation
        let schedule: [NoticeSchedule]
        let benefits: [String]
        let qualityIssues: [String]
        let categoryPath: [String]
        let contexts: [NoticeContext]
        let edition: Int?
        let organizationLinks: [NoticeContext]
        let evidence: [NoticeEvidence]
        let sources: [NoticeSource]
        init(_ value: NoticeModel) {
            version = 1
            provenance = value.descriptionProvenance
            id = value.id
            title = value.title
            aiDescription = value.aiDescription
            demoVisible = value.demoVisible
            favoriteOrganizationId = value.favoriteOrganizationId
            sourceIds = value.sourceIds
            targetUser = value.targetUser
            participationCondition = value.participationCondition
            applicationInformation = value.applicationInformation
            location = value.location
            schedule = value.schedule
            benefits = value.benefits
            qualityIssues = value.qualityIssues
            categoryPath = value.categoryPath
            contexts = value.contexts
            edition = value.edition
            organizationLinks = value.organizationLinks
            evidence = value.evidence
            sources = value.sources
        }
        var value: NoticeModel {
            NoticeModel(id: id,
                        title: title,
                        aiDescription: aiDescription,
                        demoVisible: demoVisible,
                        favoriteOrganizationId: favoriteOrganizationId,
                        sourceIds: sourceIds,
                        targetUser: targetUser,
                        participationCondition: participationCondition,
                        applicationInformation: applicationInformation,
                        location: location,
                        schedule: schedule,
                        benefits: benefits,
                        qualityIssues: qualityIssues,
                        categoryPath: categoryPath,
                        contexts: contexts,
                        edition: edition,
                        organizationLinks: organizationLinks,
                        evidence: evidence,
                        sources: sources)
        }
    }
}
