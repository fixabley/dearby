struct FavoriteOrganizationCardState: Identifiable {
    let id: String
    let name: String
    let ancestorNames: String
    let notices: [FavoriteNoticeRowState]
}
struct FavoriteNoticeRowState: Identifiable {
    let id: String
    let title: String
    let category: String
    let contextNames: String
}
