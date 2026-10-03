import SwiftUI

struct CalendarConflictView: View {
    @State private var state: CalendarConflictState
    @Environment(\.dismiss) private var dismiss
    init(schedules: [ActivityScheduleModel]) {
        _state = State(initialValue: CalendarConflictState(schedules: schedules))
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("예시 바쁜 시간만 비교해요. 실제 캘린더를 읽거나 권한을 요청하지 않아요.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("비교할 캘린더").font(.headline)
                        Toggle("예시 캘린더", isOn: $state.selected).frame(minHeight: 44)
                        Text("2026년 10월 24일 14:00~15:00 · Asia/Seoul")
                            .font(.caption).foregroundStyle(.secondary)
                        if !state.selected { Text("비교할 캘린더를 하나 이상 선택해 주세요.").font(.caption) }
                        Button("선택한 캘린더로 확인") { state.compare() }
                            .buttonStyle(DearbyButtonStyle()).disabled(!state.selected)
                    }
                    if state.compared {
                        if state.overlaps.isEmpty {
                            Label("예시 캘린더와 겹치는 시간이 없어요", systemImage: "checkmark.circle")
                            Text("고정 예시 기준이며 실제 개인 일정과는 무관해요.").font(.caption).foregroundStyle(.secondary)
                        } else { overlap }
                    }
                }.padding(20)
            }
            .navigationTitle("겹치는 시간 확인하기").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
    }
    @ViewBuilder private var overlap: some View {
        if state.overlaps.indices.contains(state.index) {
            let item = state.overlaps[state.index]
            VStack(alignment: .leading, spacing: 16) {
                Text("겹치는 시간 \(state.index + 1) / \(state.overlaps.count)").font(.headline)
                Text(item.activity.title).font(.title3.bold())
                timeRow("활동 일정", start: item.activity.start, end: item.activity.end, zone: item.activity.timeZone)
                timeRow("예시 바쁜 시간", start: item.busy.start, end: item.busy.end, zone: item.activity.timeZone)
                timeRow("겹치는 시간", start: item.start, end: item.end, zone: item.activity.timeZone)
                    .padding().background(DearbyStyle.mint, in: RoundedRectangle(cornerRadius: 12))
                HStack {
                    if state.index > 0 { Button("이전") { state.index -= 1 }.frame(minHeight: 44) }
                    Spacer()
                    Button(state.index + 1 == state.overlaps.count ? "확인 완료" : "다음 겹침") {
                        if state.index + 1 == state.overlaps.count { dismiss() } else { state.index += 1 }
                    }.frame(minHeight: 44)
                }
            }
        }
    }
    private func timeRow(_ title: String, start: Date, end: Date, zone: String) -> some View {
        let format = Date.FormatStyle(date: .abbreviated, time: .shortened, locale: Locale(identifier: "ko_KR"),
                                     timeZone: TimeZone(identifier: zone) ?? .current)
        return VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.subheadline.bold())
            Text(start.formatted(format) + " → " + end.formatted(format)).font(.subheadline)
            Text(zone).font(.caption).foregroundStyle(.secondary)
        }
    }
}
