struct NoticeContext: Decodable {
    let organizationId: String
    let role: String
    var basis: String? = nil
    var note: String? = nil

    var label: String {
        switch role {
        case "venue_institution": "개최 기관"
        case "audience_institution": "참여 대상 기관"
        case "co_operator": "공동 운영"
        default: "행사 관련 기관"
        }
    }
}
