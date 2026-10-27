import ActivityKit
import SwiftUI
import WidgetKit

/// 행사 당일 진행 중 표시 (contract "행사 당일 진행 중 표시 › 표시 디자인 — 2026-10-27"): the Live Activity's looks, laid out like the 내 활동 진행 트랙.
/// 세션 중: 세션명 > 트랙명 > 시간·위치 with the remaining time. 이동 (any gap before the next favorited session, the first one included): a walking
/// figure and the time to its start in 청록 with where to go, then 세션명 > 트랙명. The 잠금 화면 puts the day's session track on top; the expanded
/// Dynamic Island puts it under the same three lines. Compact: the track's dot (or the walking figure) and the countdown; minimal: that mark.
/// A tap opens that block's timetable sheet on 내 시간표 in the app.
struct LiveSessionWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: LiveSessionAttributes.self) { context in
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Text(context.attributes.activityTitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    Spacer(minLength: 0)
                }
                LiveSessionTrack(state: context.state)
                LiveSessionLines(attributes: context.attributes, state: context.state)
            }
            .padding(16)
            .activityBackgroundTint(Color(.systemBackground).opacity(0.85))
            .widgetURL(LiveSessionLines.link(context))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 10) {
                        LiveSessionLines(attributes: context.attributes, state: context.state)
                        LiveSessionTrack(state: context.state)
                    }.padding(.top, 4)
                }
            } compactLeading: {
                LiveSessionLines.mark(context.state)
            } compactTrailing: {
                LiveSessionLines.countdown(context.state).font(.caption2.monospacedDigit().weight(.semibold))
                    .foregroundStyle(context.state.running ? Color.primary : DearbyStyle.teal).frame(minWidth: 40)
            } minimal: {
                LiveSessionLines.mark(context.state)
            }
            .widgetURL(LiveSessionLines.link(context))
            .keylineTint(DearbyStyle.tealFill)
        }
    }
}

/// The three lines shared by the 잠금 화면 and the expanded island.
struct LiveSessionLines: View {
    let attributes: LiveSessionAttributes
    let state: LiveSessionAttributes.ContentState
    private var entry: LiveSessionAttributes.ContentState.Entry { state.current }
    var body: some View {
        if state.running { session } else { moving }
    }
    /// 세션 중: 세션명 (headline) › 트랙명 with its dot (subheadline) › `14:50 – 15:30 · 그랜드볼룸 B` (caption) and `남은 12:30`.
    private var session: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(entry.title).font(.headline).lineLimit(2)
            if let track = entry.track { Self.trackLabel(track, position: entry.trackPosition).font(.subheadline) }
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(([timeRange] + [entry.room].compactMap { $0 }).joined(separator: " · ")).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                Spacer(minLength: 0)
                (Text("남은 ") + Self.countdown(state)).font(.subheadline.monospacedDigit().weight(.semibold)).lineLimit(1)
            }
        }
    }
    /// 이동: the walking figure, `6:59 뒤 시작 · 그랜드볼룸 B로 이동` (the countdown largest, 청록) › 세션명 › 트랙명.
    private var moving: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "figure.walk").font(.title2.weight(.semibold)).foregroundStyle(DearbyStyle.teal).accessibilityLabel("이동")
            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Self.countdown(state).font(.title3.monospacedDigit().weight(.bold)).foregroundStyle(DearbyStyle.teal).multilineTextAlignment(.leading).frame(maxWidth: 76, alignment: .leading)
                    Text(["뒤 시작", Self.destination(entry)].compactMap { $0 }.joined(separator: " · "))
                        .font(.subheadline.weight(.semibold)).foregroundStyle(DearbyStyle.teal).lineLimit(1)
                }
                Text(entry.title).font(.subheadline).lineLimit(1)
                if let track = entry.track { Self.trackLabel(track, position: entry.trackPosition).font(.caption).foregroundStyle(.secondary) }
            }
        }
    }
    private var timeRange: String { Self.time(entry.start, zone: attributes.zone) + " – " + Self.time(entry.end, zone: attributes.zone) }

    /// Compact leading and minimal: the track's dot while a session runs, the walking figure in a gap.
    @ViewBuilder static func mark(_ state: LiveSessionAttributes.ContentState) -> some View {
        if state.running {
            Circle().fill(color(state.current.trackPosition)).frame(width: 10, height: 10).padding(4).accessibilityLabel("세션 중")
        } else {
            Image(systemName: "figure.walk").font(.caption.weight(.bold)).foregroundStyle(DearbyStyle.teal).accessibilityLabel("이동")
        }
    }
    static func trackLabel(_ name: String, position: Int?) -> some View {
        HStack(spacing: 6) {
            Circle().fill(color(position)).frame(width: 8, height: 8)
            Text(name).lineLimit(1)
        }
    }
    /// The track's color as the timetable shows it; 청록 for a full-width session.
    static func color(_ position: Int?) -> Color { position.map(DearbyStyle.palette) ?? DearbyStyle.teal }
    /// `그랜드볼룸 B로 이동`, else `트랙 B로 이동`; nil with neither.
    static func destination(_ entry: LiveSessionAttributes.ContentState.Entry) -> String? {
        (entry.room ?? entry.track).map { $0 + particle($0) + " 이동" }
    }
    /// `으로` after a final consonant other than ㄹ (Hangul, or a digit read so: 0·3·6), else `로`.
    static func particle(_ word: String) -> String {
        guard let last = word.unicodeScalars.last else { return "로" }
        if (0xAC00...0xD7A3).contains(last.value) {
            let final = (last.value - 0xAC00) % 28
            return final == 0 || final == 8 ? "로" : "으로"
        }
        return "036".unicodeScalars.contains(last) ? "으로" : "로"
    }
    /// Remaining time of the running session, else time to its start. The view is built at each update, so the lower bound is now (clamped
    /// to the end once stale, which reads 0:00); it only sizes the interval.
    static func countdown(_ state: LiveSessionAttributes.ContentState) -> Text {
        let end = state.running ? state.current.end : state.current.start
        let from = min(Date(), end)
        return Text(timerInterval: from...end, countsDown: true, showsHours: end.timeIntervalSince(from) >= 3600)
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

/// The day's favorited sessions as 진행 트랙 segments (`StageSegment`, as on 내 활동): before `current` 지난, `current` 지금 (in a gap too: it is the
/// one being walked to), after it 다음. Two on each side of `current` at most, `+N` for the rest.
struct LiveSessionTrack: View {
    let state: LiveSessionAttributes.ContentState
    var body: some View {
        let low = max(0, state.position - 2), high = min(state.total - 1, state.position + 2)
        HStack(spacing: 4) {
            if low > 0 { Text("+\(low)").font(.caption2).foregroundStyle(.secondary) }
            ForEach(low...max(low, high), id: \.self) { index in
                StageSegment(state: index < state.position ? .past : index == state.position ? .current : .upcoming).frame(height: 6)
            }
            if high < state.total - 1 { Text("+\(state.total - 1 - high)").font(.caption2).foregroundStyle(.secondary) }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("내 시간표 \(state.total)개 중 \(state.position + 1)번째")
    }
}
