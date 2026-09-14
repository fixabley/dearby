/// A resolved catalog projection, independent of storage and presentation state.
struct ActivityNoticeSummary {
    let notice: ActivityNotice
    let organization: ActivityOrganization?
    let contextNames: String
}
