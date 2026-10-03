import SwiftUI

struct ActivityDetailView: View {
    let state: CatalogViewModel
    let activityID: String
    @State private var showApplication = false
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
                            Text("신청 기록은 화면 체험용이며 실제 접수와 무관해요.")
                                .font(.footnote).foregroundStyle(DearbyStyle.quiet)
                            Button("신청 상태 수정") { showReport = true }.frame(minHeight: 44)
                            Text("예시 기록은 앱을 종료하면 사라집니다.").font(.caption).foregroundStyle(DearbyStyle.quiet)
                        }.padding(.horizontal, 20).padding(.bottom, 24)
                    }
                }
                .safeAreaInset(edge: .bottom, spacing: 0) { applicationActions }
                .safeAreaInset(edge: .top, spacing: 0) {
                    if applied {
                        Label("데모 신청 완료 · 실제 접수가 아닙니다", systemImage: "checkmark.circle.fill")
                            .font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
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
                ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }.accessibilityLabel("예시 활동 링크 공유")
            }
        }
        .sheet(isPresented: $showCalendar) {
            if let activity { CalendarConflictView(schedules: activity.schedules) }
        }
        .sheet(isPresented: $showApplication) {
            if let activity {
                DemoApplicationView(title: activity.title, url: ActivityModel.safeURL(activity.applicationUrl), applied: applied) {
                    state.appliedIDs.insert(activityID)
                }
            }
        }
        .background(.white).tint(DearbyStyle.teal)
        .confirmationDialog("데모 신청 상태", isPresented: $showReport, titleVisibility: .visible) {
            Button("신청했어요 (예시)") { state.appliedIDs.insert(activityID) }
            Button("신청하지 않았어요") { state.appliedIDs.remove(activityID) }
            Button("나중에", role: .cancel) {}
        } message: { Text("앱 안에서만 보여주는 예시예요. 실제 신청은 이루어지지 않아요.") }
    }
    private func identity(_ activity: ActivityModel) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                DearbyBadge(title: "예시 활동")
                DearbyBadge(title: activity.participationType == .selection ? "선발형" : "참가등록형")
            }
            Text(activity.title).font(.title.bold())
            HStack {
                Image(systemName: "infinity").font(.title).foregroundStyle(DearbyStyle.teal)
                    .frame(width: 44, height: 44).background(DearbyStyle.mint, in: Circle())
                Text("Dearby 커뮤니티").font(.headline)
                Spacer()
                Button { state.savedOrganization.toggle() } label: {
                    Label(state.savedOrganization ? "조직 저장됨" : "조직 저장", systemImage: state.savedOrganization ? "bookmark.fill" : "bookmark")
                        .font(.caption).padding(10).overlay(Capsule().stroke(DearbyStyle.teal))
                }.accessibilityIdentifier("save-organization")
            }
            Divider()
            DearbyInfoRow(title: "참가 대상", value: activity.audience, symbol: "person.2")
            DearbyInfoRow(title: "참가비", value: activity.cost, symbol: "cylinder")
            DearbyInfoRow(title: "모집 상태", value: activity.demoStatus, symbol: "calendar")
            DearbyInfoRow(title: "개최 일시", value: activity.dateLabel, symbol: "clock")
            DearbyInfoRow(title: "장소", value: activity.location, symbol: "mappin.and.ellipse")
            Divider()
            Text("소개").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.summary).lineSpacing(5)
        }
    }
    private var applicationActions: some View {
        VStack(spacing: 0) {
            Divider()
            HStack(spacing: 12) {
                Button { state.toggleSaved(activityID) } label: {
                    Image(systemName: state.savedIDs.contains(activityID) ? "bookmark.fill" : "bookmark")
                        .font(.title2).frame(width: 48, height: 50).overlay(RoundedRectangle(cornerRadius: 11).stroke(DearbyStyle.teal))
                }.accessibilityLabel("활동 저장").accessibilityIdentifier("detail-save")
                Button(applied ? "공식 사이트 보기" : "공식 사이트에서 신청") { showApplication = true }
                    .buttonStyle(DearbyButtonStyle()).accessibilityIdentifier("open-application")
            }.padding(.horizontal, 20).padding(.vertical, 12)
        }.background(.white)
    }
    private func source(_ activity: ActivityModel) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Divider()
            Text("정보 출처").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            if let url = ActivityModel.safeURL(activity.officialUrl) {
                Link(destination: url) { Label("예시 사이트 보기", systemImage: "arrow.up.right") }.frame(minHeight: 44)
                Text(url.host ?? "").font(.caption).foregroundStyle(DearbyStyle.quiet)
            }
            Text("디자인 확인용 고정 예시입니다. 모집 정보와 링크는 실제 접수 안내가 아닙니다.")
                .font(.footnote).foregroundStyle(DearbyStyle.quiet)
        }
    }
}
