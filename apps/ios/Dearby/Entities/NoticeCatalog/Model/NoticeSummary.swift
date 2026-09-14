/// A resolved catalog projection, independent of storage and presentation state.
struct NoticeSummary {
    let notice: Notice
    let organization: NoticeOrganization?
    let contextNames: String
}
