/// A value result for presentation; no persistence or observation is exposed.
enum SaveOrganizationResult {
    case saved(ActivityOrganization)
    case unresolved
}
