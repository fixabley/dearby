import SwiftUI

/// `겹치는 시간 확인`: asks for calendar access on tap, compares the activity's schedules with device events on device,
/// and forgets them when dismissed (contract "기기 캘린더 겹침 — 2026-10-06").
/// The explanation, the denied state and the no-overlap result are centred messages (contract "비스크롤 화면 배치·CTA").
/// Overlaps are a list grouped per device event (contract "겹침 결과 묶음" 2026-10-22): a summary, repeating events, then single events
/// (five, then `더 보기`); a group with several dates expands to them, and each date opens its timeline.
struct CalendarConflictView: View {
    @State private var state: CalendarConflictState
    @State private var detent = PresentationDetent.large
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.dynamicTypeSize) private var typeSize
    /// The privacy policy the intro links to (web `/privacy`); the production site unless a build passes another origin.
    private let privacyURL: URL
    @State private var showingPrivacy = false
    @State private var showsAllSingle = false
    /// `activityLink`: the activity's page; device events whose notes or URL carry it (added from the app) are not compared.
    init(schedules: [ActivityScheduleModel], activityLink: URL, source: any CalendarEventSource = DeviceCalendarEventSource(),
         privacyURL: URL = URL(string: AppOrigins.defaultWeb)!.appending(path: "privacy")) {
        _state = State(initialValue: CalendarConflictState(schedules: schedules, excluding: activityLink.absoluteString, source: source))
        self.privacyURL = privacyURL
    }
    var body: some View {
        NavigationStack {
            Group {
                switch state.phase {
                case .intro, .requesting: intro
                case .denied: denied
                case .compared where state.overlaps.isEmpty: clear
                case .compared: result
                }
            }
            .background(Color(.systemBackground))
            .navigationTitle(state.phase == .compared ? "겹치는 시간" : "겹치는 시간 확인하기").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
            .sheet(isPresented: $showingPrivacy) { SafariView(url: privacyURL).ignoresSafeArea() }
        }
        .presentationDetents([.height(690), .large], selection: $detent)
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .onChange(of: state.phase) { _, phase in
            detent = phase == .compared && !typeSize.isAccessibilitySize ? .height(690) : .large
        }
    }
    private var intro: some View {
        CenteredMessage(symbol: "calendar", title: "일정 \(state.schedules.count)개와 겹치는 시간을 찾아요",
                        message: "기기 캘린더를 이 기기 안에서만 읽어 비교해요. 일정 제목과 시각은 서버로 보내거나 앱에 저장하지 않고 이 화면을 닫으면 버려요. 캘린더에 일정을 추가하거나 바꾸지 않아요.") {
            Button { Task { await state.compare() } } label: {
                Text(state.phase == .requesting ? "캘린더를 읽고 있어요…" : "캘린더 읽기 허용하고 확인").frame(maxWidth: .infinity)
            }.buttonStyle(.borderedProminent).disabled(state.phase == .requesting)
            Button { showingPrivacy = true } label: { Text("개인정보 처리방침 보기").frame(maxWidth: .infinity, minHeight: 44) }
                .buttonStyle(.borderless).tint(DearbyStyle.teal)
                .accessibilityHint("캘린더 읽기 범위가 적힌 처리방침을 앱 안 브라우저로 열어요")
        }
    }
    private var denied: some View {
        CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "캘린더 읽기가 허용되지 않았어요",
                        message: "설정 > Dearby > 캘린더에서 전체 접근을 켜면 겹치는 시간을 확인할 수 있어요. 허용하지 않아도 신청은 그대로 할 수 있어요.") {
            if let url = URL(string: UIApplication.openSettingsURLString) {
                Button { openURL(url) } label: { Text("설정 열기").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
            }
            Button { dismiss() } label: { Text("닫기").frame(maxWidth: .infinity, minHeight: 44) }.buttonStyle(.borderless).tint(DearbyStyle.teal)
        }
    }
    /// Nothing overlaps: one confirmation screen.
    private var clear: some View {
        CenteredMessage(symbol: "checkmark.circle", title: "기기 캘린더와 겹치는 시간이 없어요",
                        message: "활동 일정 시간에 다른 일정이 없어요. 기기 캘린더는 읽기만 하고, 읽은 일정은 이 화면을 닫으면 버려요.",
                        primary: MessageAction("확인했어요") { dismiss() })
    }
    private var result: some View {
        List {
            Section {
                Text(state.summary).font(.dearby(.headline)).accessibilityIdentifier("overlap-summary")
            } footer: {
                Text("기기 캘린더는 읽기만 하고, 읽은 일정은 이 화면을 닫으면 버려요.")
            }
            if !state.repeating.isEmpty {
                Section("반복 일정") { ForEach(state.repeating) { row($0) } }
            }
            if !state.single.isEmpty {
                Section("단일 일정") {
                    ForEach(showsAllSingle ? state.single : Array(state.single.prefix(5))) { row($0) }
                    if !showsAllSingle, state.single.count > 5 {
                        Button("더 보기 (\(state.single.count - 5)개)") { showsAllSingle = true }.foregroundStyle(DearbyStyle.teal)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            Button { dismiss() } label: { Text("확인했어요").frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).controlSize(.large).tint(DearbyStyle.tealFill).font(.dearby(.body).weight(.semibold))
                .padding(.horizontal, 24).padding(.vertical, 12).background(.bar)
        }
    }
    /// `제목 · 매주 화 10:00~11:00` over `n번 겹침(첫 날짜~마지막 날짜)`; one date opens its timeline, several expand first.
    @ViewBuilder private func row(_ group: CalendarOverlapGroup) -> some View {
        let label = VStack(alignment: .leading, spacing: 4) {
            Text([group.title, group.repeats].compactMap { $0 }.joined(separator: " · ")).font(.dearby(.body))
            Text(group.overlaps.count == 1 ? group.overlaps.first.map(when) ?? "" : group.countText)
                .font(.dearby(.subheadline)).foregroundStyle(.secondary)
        }.padding(.vertical, 2)
        if group.overlaps.count == 1, let item = group.overlaps.first {
            NavigationLink { detail(item) } label: { label }
        } else {
            DisclosureGroup {
                ForEach(group.overlaps) { item in NavigationLink { detail(item) } label: { Text(when(item)).font(.dearby(.subheadline)) } }
            } label: { label }
        }
    }
    /// `10월 24일 금요일 14:00–15:00 · 30분`.
    private func when(_ item: CalendarOverlapState) -> String {
        "\(item.start.formatted(item.dateFormat)) \(item.time(item.start))–\(item.time(item.end)) · \(item.minutes)분"
    }
    /// One overlap with its timeline.
    private func detail(_ item: CalendarOverlapState) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Label(item.start.formatted(item.dateFormat), systemImage: "calendar").font(.dearby(.subheadline)).foregroundStyle(.secondary)
                Text("\(item.minutes)분이 겹쳐요").font(.dearby(.title2).bold()).accessibilityIdentifier("overlap-duration")
                Text("\(item.time(item.start)) – \(item.time(item.end))에 ‘\(item.busyTitle)’과 겹쳐요.")
                    .font(.dearby(.body)).foregroundStyle(.secondary)
                CalendarTimelineView(item: item).padding(.top, 4)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(20)
        }
        .background(Color(.systemBackground))
        .navigationTitle(item.activity.title).navigationBarTitleDisplayMode(.inline)
    }
}

#Preview("겹침 있음") {
    CalendarConflictView(
        schedules: [ActivityScheduleModel(id: "s1", title: "오리엔테이션", start: FixtureCalendarEventSource.exampleBusy.start.addingTimeInterval(1800),
                                          end: FixtureCalendarEventSource.exampleBusy.end.addingTimeInterval(3600), dateLabel: "", timeZone: "Asia/Seoul")],
        activityLink: ActivityModel.calendarLink("1"), source: FixtureCalendarEventSource()
    )
}

#Preview("권한 거부") {
    CalendarConflictView(
        schedules: [ActivityScheduleModel(id: "s1", title: "오리엔테이션", start: .now, end: .now.addingTimeInterval(3600), dateLabel: "", timeZone: "Asia/Seoul")],
        activityLink: ActivityModel.calendarLink("1"), source: FixtureCalendarEventSource(access: .denied)
    )
}
