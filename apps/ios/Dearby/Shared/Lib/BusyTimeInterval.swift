import Foundation

/// The only personal-calendar value allowed beyond the provider: two absolute instants.
struct BusyTimeInterval: Equatable, Sendable {
    let start: Date
    let end: Date
    init?(start: Date, end: Date) {
        guard start.timeIntervalSinceReferenceDate.isFinite, end.timeIntervalSinceReferenceDate.isFinite, start < end else { return nil }
        self.start = start; self.end = end
    }
    func clipped(to range: DateInterval) -> Self? {
        Self(start: max(start, range.start), end: min(end, range.end))
    }
    static func merged(_ values: [Self], in range: DateInterval) -> [Self] {
        let sorted = values.compactMap { $0.clipped(to: range) }.sorted { $0.start < $1.start }
        var result: [Self] = []
        for value in sorted {
            if let last = result.last, value.start <= last.end {
                result[result.count - 1] = Self(start: last.start, end: max(last.end, value.end))!
            } else { result.append(value) }
        }
        return result
    }
}
