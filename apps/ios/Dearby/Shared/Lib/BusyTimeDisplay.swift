import Foundation

struct BusyTimeDisplay: Equatable, Sendable {
    enum Status: Equatable, Sendable { case hidden, loading, failed, ready }
    let status: Status
    let intervals: [BusyTimeInterval]
    let overlaps: [BusyTimeInterval]
    static let hidden = Self(status: .hidden, intervals: [], overlaps: [])
    static let loading = Self(status: .loading, intervals: [], overlaps: [])
    static let failed = Self(status: .failed, intervals: [], overlaps: [])
}
