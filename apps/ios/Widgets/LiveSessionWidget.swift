import ActivityKit
import SwiftUI
import WidgetKit

/// 행사 당일 진행 중 표시 (contract "행사 당일 진행 중 표시"): the Live Activity's looks. 잠금 화면 and the expanded Dynamic Island show the activity name,
/// `지금 · 트랙 B · 그랜드볼룸 B · 세션 제목` with `남은 12:30` counting down (`Text(timerInterval:)`, the OS keeps it moving) and `다음 14:50 · …`.
/// Compact: the app symbol and the remaining time; minimal: the remaining time. A tap opens that block's timetable sheet on 찜한 세션 in the app.
struct LiveSessionWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: LiveSessionAttributes.self) { context in
            LiveSessionLines(attributes: context.attributes, state: context.state)
                .padding(16)
                .activityBackgroundTint(Color(.systemBackground).opacity(0.85))
                .widgetURL(LiveSessionLines.link(context))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.bottom) {
                    LiveSessionLines(attributes: context.attributes, state: context.state).padding(.top, 4)
                }
            } compactLeading: {
                Image("DearbySymbol").resizable().scaledToFit().padding(2)
            } compactTrailing: {
                LiveSessionLines.countdown(context.state).font(.caption2.monospacedDigit().weight(.semibold)).frame(minWidth: 40)
            } minimal: {
                LiveSessionLines.countdown(context.state).font(.caption2.monospacedDigit().weight(.semibold))
            }
            .widgetURL(LiveSessionLines.link(context))
            .keylineTint(Color(red: 0, green: 0.498, blue: 0.502))
        }
    }
}

/// The two lines under the activity name, shared by the lock screen and the expanded island.
struct LiveSessionLines: View {
    let attributes: LiveSessionAttributes
    let state: LiveSessionAttributes.ContentState
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(attributes.activityTitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(Self.line(state.current, when: state.running ? "지금" : Self.time(state.current.start, zone: attributes.zone)))
                    .font(.subheadline.weight(.semibold)).lineLimit(2)
                Spacer(minLength: 0)
                (Text(state.running ? "남은 " : "시작까지 ") + Self.countdown(state)).font(.subheadline.monospacedDigit()).lineLimit(1)
            }
            if let next = state.next {
                Text(Self.line(next, when: "다음 " + Self.time(next.start, zone: attributes.zone))).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
        }
    }
    /// `{when} · {track} · {room} · {title}` with only the parts the session has.
    static func line(_ entry: LiveSessionAttributes.ContentState.Entry, when: String) -> String {
        [when, entry.track, entry.room, entry.title].compactMap { $0 }.joined(separator: " · ")
    }
    /// Remaining time of the running session, else time to its start. The lower bound only sizes the interval, so it never follows now.
    static func countdown(_ state: LiveSessionAttributes.ContentState) -> Text {
        let end = state.running ? state.current.end : state.current.start
        return Text(timerInterval: min(state.current.start, end)...end, countsDown: true, showsHours: end.timeIntervalSince(state.current.start) >= 3600)
    }
    static func link(_ context: ActivityViewContext<LiveSessionAttributes>) -> URL? {
        LiveSessionAttributes.link(activityID: context.attributes.activityID, scheduleID: context.state.current.scheduleID)
    }
    static func time(_ date: Date, zone: String) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: zone) ?? TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
}
