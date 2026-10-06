import SwiftUI

struct ActivityDetailView: View {
    let state: CatalogViewModel
    let activityID: String
    @State private var showReport = false
    @State private var showCalendar = false
    private var activity: ActivityModel? { state.activities.first { $0.id == activityID } }
    private var applied: Bool { state.appliedIDs.contains(activityID) }
    var body: some View {
        Group {
            if let activity {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        ActivityArtwork(activityID: activity.id)
                            .frame(height: 210).clipped()
                        VStack(alignment: .leading, spacing: 24) {
                            identity(activity)
                            ActivityInformationView(activity: activity, checkCalendar: { showCalendar = true })
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
                        .background(Color(red: 0.9, green: 0.97, blue: 0.95), ignoresSafeAreaEdges: [])
                    }
                }
            }
        }
        .navigationTitle("활동 상세").navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .toolbarBackground(.white, for: .navigationBar).toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            if let activity, let url = ActivityModel.safeURL(activity.officialUrl) {
                ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }.accessibilityLabel("활동 공식 안내 링크 공유")
            }
        }
        .sheet(isPresented: $showCalendar) {
            if let activity { CalendarConflictView(schedules: activity.schedules) }
        }
        .background(.white).tint(DearbyStyle.teal)
        .confirmationDialog("신청 상태", isPresented: $showReport, titleVisibility: .visible) {
            Button("신청했어요") { state.apply(activityID, true) }
            Button("신청하지 않았어요") { state.apply(activityID, false) }
            Button("나중에", role: .cancel) {}
        } message: { Text("이 앱 안에서만 남기는 표시예요. 주최 측 접수 확인이 아니에요.") }
    }
    private func identity(_ activity: ActivityModel) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                DearbyBadge(title: activity.statusLabel(at: state.loadedAt))
                DearbyBadge(title: activity.participationType == .selection ? "선발형" : "바로 신청")
            }
            Text(activity.title).font(.title.bold())
            HStack {
                Image(systemName: "building.2").font(.title2).foregroundStyle(DearbyStyle.teal)
                    .frame(width: 44, height: 44).background(DearbyStyle.mint, in: Circle())
                Text(activity.organization ?? "주최 정보 미확인").font(.headline)
            }
            Divider()
            DearbyInfoRow(title: "참가 대상", value: activity.audience ?? "미확인", symbol: "person.2")
            DearbyInfoRow(title: "참가비", value: activity.cost ?? "미확인", symbol: "cylinder")
            DearbyInfoRow(title: "모집 상태", value: activity.statusLabel(at: state.loadedAt), symbol: "calendar")
            DearbyInfoRow(title: "개최 일시", value: activity.dateLabel.isEmpty ? "미확인" : activity.dateLabel, symbol: "clock")
            DearbyInfoRow(title: "장소", value: activity.location ?? "미확인", symbol: "mappin.and.ellipse")
            Divider()
            Text("소개").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.summary).lineSpacing(5)
        }
    }
    /// Recruiting with a safe link: apply on the official site. Otherwise only the official notice.
    @ViewBuilder private func applicationActions(_ activity: ActivityModel) -> some View {
        let apply = activity.applicationURL(at: state.loadedAt)
        if let url = apply ?? ActivityModel.safeURL(activity.officialUrl) {
            VStack(spacing: 0) {
                Divider()
                Link(destination: url) {
                    Text(apply == nil ? "공식 안내 보기" : "공식 사이트에서 신청").frame(maxWidth: .infinity)
                }
                .buttonStyle(DearbyButtonStyle()).accessibilityIdentifier("open-application")
                .accessibilityHint("외부 브라우저로 열려요")
                .padding(.horizontal, 20).padding(.vertical, 12)
            }.background(.white)
        }
    }
    private func source(_ activity: ActivityModel) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Divider()
            Text("정보 출처").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            if let url = ActivityModel.safeURL(activity.officialUrl) {
                Link(destination: url) { Label("공식 안내 보기", systemImage: "arrow.up.right") }.frame(minHeight: 44)
                Text(url.host ?? "").font(.caption).foregroundStyle(DearbyStyle.quiet)
            }
            Text(activity.sourceNote.isEmpty ? "신청 조건과 최신 일정은 공식 사이트에서 확인해 주세요." : activity.sourceNote)
                .font(.footnote).foregroundStyle(DearbyStyle.quiet)
            Text("확인 시각: " + (ActivityModel.instant(activity.sourceCheckedAt).map(ActivityText.checked) ?? "미확인"))
                .font(.caption).foregroundStyle(DearbyStyle.quiet)
        }
    }
}
