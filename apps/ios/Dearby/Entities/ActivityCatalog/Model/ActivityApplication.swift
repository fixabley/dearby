struct ActivityApplication: Decodable {
    let summary: String
    let opensAt: String?
    let opensOn: String?
    let closesAt: String?
    let closesOn: String?
    let timezone: String?
    let url: String?
}
