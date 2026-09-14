struct NoticeSource: Codable {
    let id: String
    let url: String
    var kind: String? = nil
    var checkedAt: String? = nil
    var access: String? = nil
    var note: String? = nil
}
