import Foundation

/// Application-only display projection; source calendar dates stay in NoticeModel.
struct NoticeApplicationState {
    let title: String
    let original: String
    let url: URL?
    let time: EventPeriodPresentation
    let period: String

    init(notice: NoticeModel) {
        let application = notice.applicationInformation
        title = notice.title
        original = application.summary
        url = NoticePlaceState.safeOnlineURL(application.url)
        time = EventPeriodPresentation(startsAt: application.opensAt, startsOn: application.opensOn,
            endsAt: application.closesAt, endsOn: application.closesOn, timezone: application.timezone)
        period = CompactPeriod.period(start: application.opensAt ?? application.opensOn,
            end: application.closesAt ?? application.closesOn, timezone: application.timezone, fallback: application.summary)
    }
}
