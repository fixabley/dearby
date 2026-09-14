struct NoticeApplication: Decodable {
    let summary: String
    let opensAt: String?
    let opensOn: String?
    let closesAt: String?
    let closesOn: String?
    let timezone: String?
    let url: String?
    var channels: [String]? = nil
    var requiredDocuments: [String]? = nil
    var submissionLocations: [String]? = nil
}
