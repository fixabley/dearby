import SwiftUI

struct ActivityDetailView: View {
    let state: ActivityViewModel
    let activityID: String
    /// 사용 통계 `source` of `activity_view`, `myactivity_add` and `apply_click`: where the detail was opened from (a link or a notification
    /// overrides it, `ActivityViewModel.takeLinkSource`).
    var source = "discovery"
    @State private var openedFrom: String?
    @State private var showCalendar = false
    @State private var calendarDraft: CalendarEventDraft?
    @State private var timetable: TimetableRoute?
    private var activity: ActivityModel? { state.activity(activityID) }
    private var applied: Bool { state.appliedIDs.contains(activityID) }
    var body: some View {
        Group {
            if let activity {
                ScrollView {
                    // No header image (contract "활동 상세 이미지 섹션 제거" 2026-10-19 밤): title, organization and status come first.
                    VStack(alignment: .leading, spacing: 24) {
                        VStack(alignment: .leading, spacing: 24) {
                            identity(activity)
                            if let eligibility = activity.eligibility { audience(eligibility) }
                            ActivityInformationView(rows: scheduleRows(activity), canCheckCalendar: !activity.overlapSchedules.isEmpty,
                                                    checkCalendar: { showCalendar = true }, addToCalendar: { calendarDraft = $0 },
                                                    openTimetable: { timetable = TimetableRoute(activityID: activity.id, scheduleID: $0.id, title: "\($0.title) 시간표") })
                            place(activity)
                            source(activity)
                        }.padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 24)
                    }
                }
                .safeAreaInset(edge: .bottom, spacing: 0) { applicationActions(activity) }
            } else {
                // Only a linked id the server answered 404 for lands here (`ActivityViewModel.openLinked`); wording as the web detail.
                CenteredMessage(symbol: "info.circle", title: "활동을 찾을 수 없어요", message: "삭제되었거나 더 이상 공개되지 않는 활동이에요.")
            }
        }
        .navigationTitle("활동 상세").navigationBarTitleDisplayMode(.inline)
        .analyticsScreen("activity_detail")
        .onAppear {
            guard openedFrom == nil else { return }
            let from = state.takeLinkSource(activityID) ?? source
            openedFrom = from
            AppAnalytics.event("activity_view", parameters(["source": from]))
        }
        .toolbar(.visible, for: .navigationBar)
        .toolbar {
            // The activity's Dearby page, not the official notice (contract "유니버설 링크·App Links·활동 공유·오픈그래프"); the official
            // link stays in the source row.
            if let activity {
                ShareLink(item: state.calendarLink(activity.id), subject: Text(activity.title)) { Image(systemName: "square.and.arrow.up") }
                    .accessibilityLabel("활동 공유")
            }
        }
        .sheet(isPresented: $showCalendar) {
            if let activity { CalendarConflictView(schedules: activity.overlapSchedules, activityLink: state.calendarLink(activity.id)) }
        }
        .sheet(item: $calendarDraft) { AddToCalendarSheet(draft: $0).ignoresSafeArea() }
        // Pushed from here only, so every stack that shows a detail (발견, 내 활동, 알림) gets it without registering the route.
        .navigationDestination(item: $timetable) { TimetablePage(state: state, route: $0) }
        .background(Color(.systemBackground)).tint(DearbyStyle.teal)
    }
    private func parameters(_ extra: [String: Any]) -> [String: Any] {
        AppAnalytics.activity(activityID, activity).merging(extra) { $1 }
    }
    private func identity(_ activity: ActivityModel) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 8) {
                // 활동 상태 (contract "활동 상태" 2026-10-26); 모집 정보 없음 has no badge.
                if let badge = activity.status(at: state.loadedAt)?.badge { DearbyBadge(title: badge) }
                if let category = activity.category { DearbyBadge(title: category.label, quiet: true) }
            }
            // 분야·얻을 수 있는 것 (contract "분야·성격 태그와 아이콘" 2026-10-24): every tag as icon and text, empty groups omitted.
            tagGroup("분야", activity.fieldTags)
            tagGroup("얻을 수 있는 것", activity.traitTags)
            // The heart, bell and `+` beside the title (contract "즐겨찾기(하트)"·"`+` 내 활동에 추가" 2026-10-25: the toolbar's 추가 is gone).
            HStack(alignment: .top, spacing: 8) {
                Text(activity.title).font(.title.bold()).frame(maxWidth: .infinity, alignment: .leading)
                FavoriteControls(target: .activity(activity.id), mine: (applied, { state.addToMine(activity.id, source: openedFrom ?? source) }))
            }
            Text(activity.organization).font(.headline).foregroundStyle(.secondary)
            Divider()
            DearbyInfoRow(title: "모집 상태", value: statusText(activity), symbol: "calendar")
            DearbyInfoRow(title: "개최 일시", value: activity.scheduleText(at: state.loadedAt) ?? "미확인", symbol: "clock")
            Divider()
            Text("소개").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.summary).lineSpacing(5)
        }
    }
    /// The 모집 상태 row: the status, `모집 일정 미정` for 모집 정보 없음, `모집 여부 확인 필요` without one (an unreadable window, older server).
    private func statusText(_ activity: ActivityModel) -> String {
        guard let status = activity.status(at: state.loadedAt) else { return "모집 여부 확인 필요" }
        return status.badge ?? "모집 일정 미정"
    }
    @ViewBuilder private func tagGroup(_ title: String, _ tags: [Tag]) -> some View {
        if !tags.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                Text(title).font(.dearby(.caption).weight(.semibold)).foregroundStyle(.secondary).accessibilityAddTraits(.isHeader)
                ChipFlow(spacing: 6) { ForEach(tags) { tag in DearbyTagChip(title: tag.name, nameEn: tag.nameEn) } }
                    .accessibilityElement(children: .ignore).accessibilityLabel(tags.map(\.name).joined(separator: ", "))
            }
        }
    }
    /// Who may apply (contract "대상자 조건"): one chip per condition, `any` groups joined with 또는.
    @ViewBuilder private func audience(_ eligibility: Eligibility) -> some View {
        let chips = EligibilityText.chips(eligibility)
        if !chips.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                Divider()
                Text("대상").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.teal)
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(chips, id: \.self) { chip in DearbyBadge(title: chip) }
                }.accessibilityElement(children: .combine).accessibilityLabel("대상 " + chips.joined(separator: ", "))
            }
        }
    }
    /// The CTA opens the application link, else the official notice (contract "신청 확인 조건 완화" 2026-10-19 밤). Unless applying
    /// is over it reads as applying and arms the "신청하셨나요?" prompt for when the app comes back from the browser.
    /// Adding is the `+` beside the title (contract "`+` 내 활동에 추가" 2026-10-25). Once applied the block grows upward with a teal
    /// `checkmark.circle.fill` "이미 추가된 항목입니다" line whose trailing `내 활동으로 >` opens it in 내 활동; removing is only in 내 활동.
    @ViewBuilder private func applicationActions(_ activity: ActivityModel) -> some View {
        let site = activity.applicationSiteURL
        let closed = activity.applicationClosed(at: state.loadedAt)
        VStack(spacing: 10) {
            Divider().padding(.bottom, 2)
            if applied {
                HStack {
                    Label("이미 추가된 항목입니다", systemImage: "checkmark.circle.fill").accessibilityIdentifier("mine-added")
                    Spacer()
                    Button { state.showInMine = activity.id } label: {
                        HStack(spacing: 2) { Text("내 활동으로"); Image(systemName: "chevron.right") }
                    }
                    .buttonStyle(.borderless).accessibilityLabel("내 활동으로 가기").accessibilityIdentifier("show-in-mine")
                }
                .font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
            }
            if let url = site ?? activity.officialURL {
                Link(destination: url) {
                    Text(closed ? "공식 안내 보기" : site != nil ? "신청 사이트 열기" : "공식 사이트에서 신청").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent).tint(DearbyStyle.tealFill).accessibilityIdentifier("open-application")
                .accessibilityHint("외부 브라우저로 열려요")
                .environment(\.openURL, OpenURLAction { opened in
                    if !closed {
                        AppAnalytics.event("apply_click", parameters(["source": openedFrom ?? source]))
                        state.openedApplication(activity.id)
                    }
                    return .systemAction(opened)
                })
            }
            Text("직접 남기는 기록이며 주최 측 확인은 아니에요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
        }
        .controlSize(.large).font(.dearby(.body).weight(.semibold)).tint(DearbyStyle.teal)
        .padding(.horizontal, 20).padding(.bottom, 12).background(Color(.systemBackground))
    }
    /// The `행사 일정` rows in track order: the recruitment (`모집` with the period and a D-day while recruiting when both bounds are known,
    /// else `모집 마감` with the deadline — contract "모집 날짜 표기 — 2026-10-26"), `schedules[]`, the activity period; each dated one
    /// can be added to the calendar (the recruitment row as its deadline).
    private func scheduleRows(_ activity: ActivityModel) -> [ActivityInformationView.Row] {
        let now = state.loadedAt
        let period = activity.applicationStage?.periodText(now: now) == nil ? nil : activity.recruitmentDate(at: now)
        return (ActivityStages.build(activity, marks: [:], now: now)?.stages ?? []).map { stage in
            let application = stage.id == ActivityStages.applicationStageID
            return ActivityInformationView.Row(id: stage.id, title: application ? (period == nil ? "모집 마감" : "모집") : stage.name,
                                               date: application ? period?.text ?? stage.dateText(now: now) : stage.dateText(now: now),
                                               draft: stage.calendarDraft(activity, link: state.calendarLink(activity.id)),
                                               hasTimetable: !application && stage.source.hasTimetable, badge: application ? period?.dday : nil)
        }
    }
    /// 장소 (contract "활동 장소"): the curated place's name and address open Apple Maps; otherwise the collected text, if any.
    @ViewBuilder private func place(_ activity: ActivityModel) -> some View {
        if activity.place != nil || activity.location != nil {
            VStack(alignment: .leading, spacing: 12) {
                Divider()
                Text("장소").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
                if let place = activity.place, let url = place.mapsURL {
                    Link(destination: url) {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "mappin.and.ellipse").imageScale(.large).foregroundStyle(DearbyStyle.teal).frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(place.name).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.ink)
                                Text(place.address).font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
                            }
                            Spacer(minLength: 0)
                            Image(systemName: "arrow.up.right").font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
                        }.frame(minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel("장소 \(place.name), \(place.address)").accessibilityHint("지도 앱으로 열려요")
                    .accessibilityIdentifier("open-place")
                } else if let location = activity.location {
                    Text(location).font(.dearby(.subheadline))
                }
            }
        }
    }
    private func source(_ activity: ActivityModel) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Divider()
            Text("정보 출처").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            if let url = activity.officialURL {
                Link(destination: url) { Label("공식 안내 보기", systemImage: "arrow.up.right") }.frame(minHeight: 44)
                Text(url.host ?? "").font(.caption).foregroundStyle(DearbyStyle.quiet)
            }
            Text("신청 조건과 최신 일정은 공식 사이트에서 확인해 주세요.")
                .font(.footnote).foregroundStyle(DearbyStyle.quiet)
        }
    }
}
