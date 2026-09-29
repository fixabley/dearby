import SwiftUI

struct CalendarConflictView: View {
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
                    Text("개인 일정의 제목과 내용은 가져오지 않고, 바쁜 시간만 이 화면에서 비교해요.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    if state.unknownCount > 0 && !state.windows.isEmpty {
                        Text("시간이 확인되지 않은 일정 \(state.unknownCount)개는 비교에서 제외됐어요.")
                            .foregroundStyle(.orange)
                    }
                    content
                }.padding(20)
            }
            .navigationTitle("겹치는 시간 확인하기").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { state.clear(); dismiss() } } }
        }
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
            choices
            if state.phase == .result {
                if state.overlaps.isEmpty {
                    Label(state.unknownCount == 0 ? "선택한 캘린더와 겹치는 시간이 없어요" : "확인 가능한 시간에는 겹침이 없어요", systemImage: "checkmark.circle")
                    Text("기기에 동기화된 선택한 캘린더 기준이에요.").font(.caption).foregroundStyle(.secondary)
                } else { overlap }
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
                Text("겹치는 시간 \(state.index + 1) / \(state.overlaps.count)").font(.headline)
                Text(item.activity.title).font(.title3.bold())
                timeRow("활동 일정", start: item.activity.start, end: item.activity.end, zone: item.activity.timeZone)
                timeRow("내 바쁜 시간", start: item.busy.start, end: item.busy.end, zone: item.activity.timeZone)
                timeRow("겹치는 시간", start: item.start, end: item.end, zone: item.activity.timeZone)
                    .padding().background(DearbyStyle.mint, in: RoundedRectangle(cornerRadius: 12))
                HStack {
                    if state.index > 0 { Button("이전") { state.index -= 1 }.frame(minHeight: 44) }
                    Spacer()
                    Button(state.index + 1 == state.overlaps.count ? "확인 완료" : "다음 겹침") {
                        if state.index + 1 == state.overlaps.count { state.clear(); dismiss() } else { state.index += 1 }
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
