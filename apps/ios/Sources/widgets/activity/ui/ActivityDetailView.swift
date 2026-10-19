import SwiftUI

struct ActivityDetailView: View {
    let state: ActivityViewModel
    let activityID: String
    @State private var showReport = false
    @State private var showCalendar = false
    private var activity: ActivityModel? { state.activity(activityID) }
    private var applied: Bool { state.appliedIDs.contains(activityID) }
    var body: some View {
        Group {
            if let activity {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        ActivityArtwork(imageURL: activity.imageURL)
                            .frame(height: 210).clipped()
                        VStack(alignment: .leading, spacing: 24) {
                            identity(activity)
                            if let eligibility = activity.eligibility { audience(eligibility) }
                            ActivityInformationView(activity: activity, checkCalendar: { showCalendar = true })
                            place(activity)
                            source(activity)
                            Divider()
                            Text("신청 표시는 이 앱 안에서만 남기는 기록이에요. 실제 접수 여부와 무관해요.")
                                .font(.footnote).foregroundStyle(DearbyStyle.quiet)
                            Button("신청 상태 수정") { showReport = true }.frame(minHeight: 44)
                            Text("신청 표시는 앱을 종료하면 사라져요.").font(.caption).foregroundStyle(DearbyStyle.quiet)
                        }.padding(.horizontal, 20).padding(.bottom, 24)
                    }
                }
                .safeAreaInset(edge: .bottom, spacing: 0) { applicationActions(activity) }
                .safeAreaInset(edge: .top, spacing: 0) {
                    if applied {
                        VStack(alignment: .leading, spacing: 4) {
                            Label("신청했다고 표시했어요 · 실제 접수 확인이 아니에요", systemImage: "checkmark.circle.fill").font(.subheadline)
                            ConfirmToggle(state: state, activityID: activityID)
                            Text(ConfirmToggle.note).font(.caption).foregroundStyle(DearbyStyle.quiet)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal).padding(.vertical, 8)
                        .background(DearbyStyle.mint, ignoresSafeAreaEdges: [])
                    }
                }
            }
        }
        .navigationTitle("활동 상세").navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .toolbarBackground(Color(.systemBackground), for: .navigationBar).toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            if let url = activity?.officialURL {
                ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }.accessibilityLabel("활동 공식 안내 링크 공유")
            }
        }
        .sheet(isPresented: $showCalendar) {
            if let activity { CalendarConflictView(schedules: activity.schedules) }
        }
        .background(Color(.systemBackground)).tint(DearbyStyle.teal)
        .confirmationDialog("신청 상태", isPresented: $showReport, titleVisibility: .visible) {
            Button("신청했어요") { state.apply(activityID, true) }
            Button("신청하지 않았어요") { state.apply(activityID, false) }
            Button("나중에", role: .cancel) {}
        } message: { Text("이 앱 안에서만 남기는 표시예요. 주최 측 접수 확인이 아니에요.") }
    }
    private func identity(_ activity: ActivityModel) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 8) {
                DearbyBadge(title: activity.statusLabel(at: state.loadedAt))
                if let category = activity.category { DearbyBadge(title: category.label, quiet: true) }
            }
            if !activity.tags.isEmpty {
                // 얻을 수 있는 것 (contract "태그"): every tag, wrapping.
                ChipFlow(spacing: 6) { ForEach(activity.tags) { tag in DearbyBadge(title: tag.name, quiet: true) } }
                    .accessibilityElement(children: .ignore).accessibilityLabel("태그 " + activity.tags.map(\.name).joined(separator: ", "))
            }
            Text(activity.title).font(.title.bold())
            HStack {
                Image(systemName: "building.2").font(.title2).foregroundStyle(DearbyStyle.teal)
                    .frame(width: 44, height: 44).background(DearbyStyle.mint, in: Circle())
                Text(activity.organization).font(.headline)
            }
            Divider()
            DearbyInfoRow(title: "모집 상태", value: activity.statusLabel(at: state.loadedAt), symbol: "calendar")
            DearbyInfoRow(title: "개최 일시", value: activity.dateLabel.isEmpty ? "미확인" : activity.dateLabel, symbol: "clock")
            Divider()
            Text("소개").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.summary).lineSpacing(5)
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
    /// Recruiting with a safe link: apply on the official site. Otherwise only the official notice.
    @ViewBuilder private func applicationActions(_ activity: ActivityModel) -> some View {
        let apply = activity.applicationURL(at: state.loadedAt)
        if let url = apply ?? activity.officialURL {
            VStack(spacing: 0) {
                Divider()
                Link(destination: url) {
                    Text(apply == nil ? "공식 안내 보기" : "공식 사이트에서 신청").frame(maxWidth: .infinity)
                }
                .buttonStyle(DearbyButtonStyle()).accessibilityIdentifier("open-application")
                .accessibilityHint("외부 브라우저로 열려요")
                // Only the application link (not the official notice) leads to the "신청하셨나요?" prompt.
                .environment(\.openURL, OpenURLAction { opened in
                    if apply != nil { state.openedApplication(activity.id) }
                    return .systemAction(opened)
                })
                .padding(.horizontal, 20).padding(.vertical, 12)
            }.background(Color(.systemBackground))
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
                            Image(systemName: "mappin.and.ellipse").font(.title3).foregroundStyle(DearbyStyle.teal).frame(width: 28)
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
