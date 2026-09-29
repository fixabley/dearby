import SwiftUI

struct CalendarConflictView: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var state: CalendarConflictState
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    init(schedules: [ActivityScheduleModel]) {
        _state = State(initialValue: CalendarConflictState(schedules: schedules))
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if state.phase != .result {
                        Text("개인 일정의 제목과 내용은 가져오지 않고, 바쁜 시간만 이 화면에서 비교해요.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    if state.unknownCount > 0 && !state.windows.isEmpty {
                        Text("시간이 확인되지 않은 일정 \(state.unknownCount)개는 비교에서 제외됐어요.")
                            .foregroundStyle(.orange)
                    }
                    content
                }.padding(20)
            }
            .id(state.phase == .result ? state.index + 1 : 0)
            .safeAreaInset(edge: .bottom) {
                if state.phase == .result && !state.overlaps.isEmpty { confirmation }
            }
            .navigationTitle("겹치는 시간").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 12) {
                        if state.phase == .result && !state.overlaps.isEmpty {
                            Text("\(state.index + 1) / \(state.overlaps.count)")
                                .foregroundStyle(.secondary).accessibilityIdentifier("overlap-position")
                        }
                        Button { state.clear(); dismiss() } label: { Image(systemName: "xmark") }
                            .accessibilityLabel("닫기")
                    }
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .task { await state.connect(request: true) }
        .onDisappear { state.clear() }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { state.clear() } else { Task { await state.connect(request: false) } }
        }
    }
    @ViewBuilder private var content: some View {
        switch state.phase {
        case .idle, .loading: ProgressView("캘린더 확인 중…")
        case .unknown:
            Label("활동의 정확한 시작·종료 시간이 없어 비교할 수 없어요.", systemImage: "clock.badge.questionmark")
            Text("공식 일정이 확인되면 다시 확인해 주세요. 겹치는 시간이 없다는 뜻은 아니에요.")
        case .permission:
            Label("캘린더 읽기 권한이 필요해요", systemImage: "calendar.badge.exclamationmark")
            Text("설정에서 Dearby의 캘린더 전체 접근을 허용해 주세요. 앱은 일정을 수정하지 않아요.")
            Link("설정 열기", destination: URL(string: UIApplication.openSettingsURLString)!)
            Button("권한 다시 확인") { Task { await state.connect(request: true) } }
        case .noCalendars:
            Text("기기에 연결된 캘린더가 없어요. 캘린더 앱에서 계정을 연결한 뒤 다시 확인해 주세요.")
            retry
        case .failed:
            Text("캘린더를 확인하지 못했어요. 권한과 연결 상태를 확인하고 다시 시도해 주세요.")
            retry
        case .choose, .result:
            if state.phase == .choose { choices }
            if state.phase == .result {
                if state.overlaps.isEmpty {
                    Label(state.unknownCount == 0 ? "선택한 캘린더와 겹치는 시간이 없어요" : "확인 가능한 시간에는 겹침이 없어요", systemImage: "checkmark.circle")
                    Text("기기에 동기화된 선택한 캘린더 기준이에요.").font(.caption).foregroundStyle(.secondary)
                } else { overlap }
                DisclosureGroup("비교할 캘린더 변경") { choices }
                    .font(.subheadline)
            }
        }
    }
    private var retry: some View { Button("다시 확인") { Task { await state.connect(request: true) } } }
    private var choices: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("비교할 캘린더").font(.headline)
            ForEach(state.calendars) { calendar in
                Toggle(calendar.title, isOn: Binding(get: { state.selected.contains(calendar.id) }, set: { _ in state.toggle(calendar.id) }))
                    .frame(minHeight: 44)
            }
            if state.selected.isEmpty { Text("비교할 캘린더를 하나 이상 선택해 주세요.").font(.caption) }
            Button("선택한 캘린더로 확인") { Task { await state.compare() } }
                .buttonStyle(DearbyButtonStyle()).disabled(state.selected.isEmpty)
        }
    }
    @ViewBuilder private var overlap: some View {
        if state.overlaps.indices.contains(state.index) {
            let item = state.overlaps[state.index]
            VStack(alignment: .leading, spacing: 16) {
                Label(item.start.formatted(Date.FormatStyle(locale: Locale(identifier: "ko_KR"),
                    timeZone: TimeZone(identifier: item.activity.timeZone) ?? .current).month().day().weekday()), systemImage: "calendar")
                Text("\(item.durationText)이 겹쳐요").font(.largeTitle.bold())
                Text("\(item.timeRange(item.start, item.end))에 다른 일정과 겹쳐요.")
                    .font(.subheadline)
                if dynamicTypeSize.isAccessibilitySize {
                    timeRow("이 활동 · " + item.activity.title, start: item.activity.start, end: item.activity.end, zone: item.activity.timeZone)
                    timeRow("연결한 캘린더 · 바쁜 시간", start: item.busy.start, end: item.busy.end, zone: item.activity.timeZone)
                    timeRow("겹치는 구간", start: item.start, end: item.end, zone: item.activity.timeZone)
                        .padding().background(.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
                } else { CalendarOverlapTimeline(item: item) }
                Text("시간대 · " + item.activity.timeZone).font(.caption).foregroundStyle(.secondary)
                Label("개인 일정의 제목은 가져오지 않으며, 캘린더 일정은 변경되지 않아요.", systemImage: "info.circle")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
    private var confirmation: some View {
        VStack(spacing: 8) {
            Button("확인했어요") {
                if state.index + 1 == state.overlaps.count { state.clear(); dismiss() } else { state.index += 1 }
            }.buttonStyle(DearbyButtonStyle())
            Text(state.index + 1 == state.overlaps.count ? "모든 겹치는 시간을 확인했어요." : "다음 겹치는 시간으로 이동해요.")
                .font(.caption).foregroundStyle(DearbyStyle.teal)
            if state.index > 0 {
                Button("이전 겹치는 시간") { state.index -= 1 }.font(.subheadline).frame(minHeight: 44)
            }
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.background)
    }
    private func timeRow(_ title: String, start: Date, end: Date, zone: String) -> some View {
        let format = Date.FormatStyle(date: .abbreviated, time: .shortened, locale: Locale(identifier: "ko_KR"),
                                     timeZone: TimeZone(identifier: zone) ?? .current)
        return VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.subheadline.bold())
            Text(start.formatted(format) + " → " + end.formatted(format)).font(.subheadline)
        }
    }
}
