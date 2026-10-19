import SwiftUI

/// `겹치는 시간 확인`: asks for calendar access on tap, compares the activity's schedules with device events on device,
/// and forgets them when dismissed (contract "기기 캘린더 겹침 — 2026-10-06").
struct CalendarConflictView: View {
    @State private var state: CalendarConflictState
    @State private var detent = PresentationDetent.large
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.dynamicTypeSize) private var typeSize
    /// The privacy policy the intro links to (web `/privacy`); the production site unless a build passes another origin.
    private let privacyURL: URL
    @State private var showingPrivacy = false
    init(schedules: [ActivityScheduleModel], source: any CalendarEventSource = DeviceCalendarEventSource(),
         privacyURL: URL = URL(string: AppOrigins.defaultWeb)!.appending(path: "privacy")) {
        _state = State(initialValue: CalendarConflictState(schedules: schedules, source: source))
        self.privacyURL = privacyURL
    }
    var body: some View {
        Group {
            switch state.phase {
            case .intro, .requesting: intro
            case .denied: denied
            case .compared: result
            }
        }
        .presentationDetents([.height(690), .large], selection: $detent)
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .onChange(of: state.phase) { _, phase in
            detent = phase == .compared && !typeSize.isAccessibilitySize ? .height(690) : .large
        }
    }
    private var intro: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("기기 캘린더를 읽어 이 활동의 일정과 겹치는 시간을 찾아요.").font(.headline)
                    VStack(alignment: .leading, spacing: 8) {
                        Label("일정은 이 기기 안에서만 비교해요.", systemImage: "iphone")
                        Label("일정 제목과 시각은 서버로 보내거나 앱에 저장하지 않고, 이 화면을 닫으면 버려요.", systemImage: "lock")
                        Label("캘린더에 일정을 추가하거나 바꾸지 않아요.", systemImage: "eye")
                    }.font(.subheadline).foregroundStyle(.secondary)
                    Text("비교할 일정 \(state.schedules.count)개").font(.caption).foregroundStyle(.secondary)
                    Button("개인정보 처리방침 보기") { showingPrivacy = true }
                        .font(.dearby(.footnote).weight(.semibold)).foregroundStyle(DearbyStyle.quiet).buttonStyle(.plain)
                        .accessibilityHint("캘린더 읽기 범위가 적힌 처리방침을 앱 안 브라우저로 열어요")
                    Button(state.phase == .requesting ? "캘린더를 읽고 있어요…" : "캘린더 읽기 허용하고 확인") {
                        Task { await state.compare() }
                    }.buttonStyle(DearbyButtonStyle()).disabled(state.phase == .requesting)
                }.padding(20)
            }
            .navigationTitle("겹치는 시간 확인하기").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
            .sheet(isPresented: $showingPrivacy) { SafariView(url: privacyURL).ignoresSafeArea() }
        }
    }
    private var denied: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Label("캘린더 읽기가 허용되지 않았어요", systemImage: "calendar.badge.exclamationmark").font(.title3.bold())
                    Text("설정 > Dearby > 캘린더에서 전체 접근을 켜면 겹치는 시간을 확인할 수 있어요. 허용하지 않아도 신청은 그대로 할 수 있어요.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        Button("설정 열기") { openURL(url) }.buttonStyle(DearbyButtonStyle())
                    }
                    Button("닫기") { dismiss() }.buttonStyle(DearbyButtonStyle(outlined: true))
                }.padding(20)
            }
            .navigationTitle("겹치는 시간 확인하기").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
    }
    private var result: some View {
        VStack(spacing: 0) {
            DearbySheetHeader(title: "겹치는 시간", detail: state.overlaps.isEmpty ? "0 / 0" : "\(state.index + 1) / \(state.overlaps.count)") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if state.overlaps.indices.contains(state.index) {
                        let item = state.overlaps[state.index]
                        Label(item.start.formatted(item.dateFormat), systemImage: "calendar")
                            .font(.subheadline)
                        Text("\(item.minutes)분이 겹쳐요").font(.largeTitle.bold())
                            .accessibilityIdentifier("overlap-duration")
                        Text("\(item.time(item.start)) – \(item.time(item.end))에 ‘\(item.busyTitle)’과 겹쳐요.")
                            .font(.subheadline)
                        CalendarTimelineView(item: item).padding(.top, 4)
                    } else {
                        Label("기기 캘린더와 겹치는 시간이 없어요", systemImage: "checkmark.circle")
                            .font(.title3.bold()).padding(.vertical, 32)
                        Text("활동 일정 시간에 다른 일정이 없어요.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading).padding(20)
            }
            VStack(spacing: 12) {
                Label("기기 캘린더는 읽기만 하고, 읽은 일정은 이 화면을 닫으면 버려요.", systemImage: "info.circle")
                    .font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
                Button("확인했어요") {
                    if !state.advance() { dismiss() }
                }.buttonStyle(DearbyButtonStyle())
                if state.index + 1 < state.overlaps.count {
                    Text("다음 겹치는 일정으로 이동해요.").font(.caption).foregroundStyle(DearbyStyle.teal)
                }
            }.padding(.horizontal, 20).padding(.bottom, 16)
        }.background(Color(.systemBackground))
    }
}

#Preview("겹침 있음") {
    CalendarConflictView(
        schedules: [ActivityScheduleModel(id: "s1", title: "오리엔테이션", start: FixtureCalendarEventSource.exampleBusy.start.addingTimeInterval(1800),
                                          end: FixtureCalendarEventSource.exampleBusy.end.addingTimeInterval(3600), dateLabel: "", timeZone: "Asia/Seoul")],
        source: FixtureCalendarEventSource()
    )
}

#Preview("권한 거부") {
    CalendarConflictView(
        schedules: [ActivityScheduleModel(id: "s1", title: "오리엔테이션", start: .now, end: .now.addingTimeInterval(3600), dateLabel: "", timeZone: "Asia/Seoul")],
        source: FixtureCalendarEventSource(access: .denied)
    )
}
