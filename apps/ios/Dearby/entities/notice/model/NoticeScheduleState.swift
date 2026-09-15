import Foundation

/// Ordered presentation projection: index remains aligned with the App-owned calendar callbacks.
struct NoticeScheduleState {
    let title: String
    let period: String
    let originalSummary: String
    let time: EventPeriodPresentation
    let places: [NoticeSchedulePlaceState]
    let onlineURL: URL?
    let location: String
    let venues: [NoticeVenue]

    init(phase: NoticePhase) {
        title = phase.period.mode == "online" && !phase.period.label.contains("온라인") ? "온라인 \(phase.period.label)" : phase.period.label
        period = CompactPeriod.period(start: phase.period.startsAt ?? phase.period.startsOn,
            end: phase.period.endsAt ?? phase.period.endsOn, timezone: phase.period.timezone, fallback: "일정 미확인")
        time = EventPeriodPresentation(startsAt: phase.period.startsAt, startsOn: phase.period.startsOn,
            endsAt: phase.period.endsAt, endsOn: phase.period.endsOn, timezone: phase.period.timezone)
        places = phase.period.mode == "online" ? [] : phase.locations.map(NoticeSchedulePlaceState.init)
        onlineURL = phase.period.mode == "online" ? NoticePlaceState.safeOnlineURL(phase.period.onlineUrl) : nil
        originalSummary = phase.period.summary
        location = phase.period.mode == "online" ? "온라인" :
            (phase.locations.isEmpty ? "장소 미확인" : phase.locations.map(\.name).joined(separator: " · "))
        venues = phase.locations.filter { $0.coordinates != nil }
    }
}

struct NoticeSchedulePlaceState {
    let venue: NoticeVenue
    let display: NoticePlaceState

    init(venue: NoticeVenue) {
        self.venue = venue
        display = NoticePlaceState(name: venue.name, address: venue.address)
    }
}
