enum SearchQuery {
    /// Every whitespace-separated term must appear in some field; case and surrounding spaces are ignored.
    static func matches(_ query: String, fields: [String]) -> Bool {
        let values = fields.map { $0.lowercased() }
        return query.lowercased().split(whereSeparator: \.isWhitespace).allSatisfy { term in values.contains { $0.contains(term) } }
    }
}
