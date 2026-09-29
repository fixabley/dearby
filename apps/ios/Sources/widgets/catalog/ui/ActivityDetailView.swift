import SwiftUI

struct ActivityDetailView: View {
    let state: CatalogState
    let activityID: String
    var showsSaving = true
    @State private var browser: ActivityBrowserDestination?
    @State private var applicationAttempt = false
    @State private var showReport = false
    @State private var message: String?
    @State private var showCalendar = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    private var activity: ActivityModel? { state.catalog?.activities.first { $0.id == activityID } }
    private var applied: Bool { state.local.applications[activityID] == .applied }
    var body: some View {
        Group {
            if let activity {
                TimelineView(.explicit(state.expirationDates)) { timeline in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 24) {
                            ActivityArtwork(url: activity.imageUrl, preview: activity.isPreview == true)
                                .frame(height: 210)
                            VStack(alignment: .leading, spacing: 24) {
                                identity(activity, at: timeline.date)
                                ActivityInformationView(activity: activity, checkCalendar: { showCalendar = true })
                                source(activity)
                                Divider()
                                Text("직접 남긴 신청 기록은 주최 측의 접수·선정·결제 확인과 달라요.")
                                    .font(.footnote).foregroundStyle(DearbyStyle.quiet)
                                Button("신청 상태 수정") { showReport = true }.frame(minHeight: 44)
                                Text("신청 기록은 이 기기에만 저장됩니다.").font(.caption).foregroundStyle(DearbyStyle.quiet)
                            }.padding(.horizontal, 20).padding(.bottom, 24)
                        }
                    }
                }
                .safeAreaInset(edge: .bottom, spacing: 0) { applicationActions(activity) }
                .safeAreaInset(edge: .top, spacing: 0) {
                    if applied { applicationBanner }
                }
            } else {
                ContentUnavailableView("활동 정보를 확인할 수 없어요", systemImage: "doc.questionmark",
                    description: Text("새로고침에서 활동이 제외되었어요. 저장한 프로그램과 신청 기록은 유지됩니다."))
            }
        }
        .navigationTitle("활동 상세").navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.white, for: .navigationBar).toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .principal) { Text("활동 상세").font(.headline).foregroundStyle(DearbyStyle.teal) }
            if let activity, let url = ActivityModel.safeURL(activity.officialUrl) {
                ToolbarItem(placement: .topBarTrailing) {
                    ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }.accessibilityLabel("공식 활동 링크 공유")
                }
            }
        }
        .sheet(isPresented: $showCalendar) {
            if let activity { CalendarConflictView(schedules: activity.schedules) }
        }
        .background(.white)
        .tint(Color(red: 0, green: 0.36, blue: 0.34))
        .sheet(item: $browser, onDismiss: {
            if applicationAttempt { applicationAttempt = false; showReport = true }
        }) { destination in ActivityBrowser(url: destination.url) }
        .confirmationDialog("신청을 완료하셨나요?", isPresented: $showReport, titleVisibility: .visible) {
            Button("신청했어요") { report(.applied) }
            Button("신청하지 않았어요") { report(.notApplied) }
            Button("나중에") {}
        } message: { Text("직접 남기는 기록이에요. 주최 측의 접수·선정·결제 확인과는 달라요.") }
        .alert("저장하지 못했어요", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("확인") { message = nil }
        } message: { Text(message ?? "") }
    }
    private func identity(_ activity: ActivityModel, at now: Date) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text(activity.isPreview == true ? "예시 활동" : activity.status(at: now))
                Text(activity.participationType == .selection ? "선발형" : "참가등록형")
            }.font(.caption.weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                .padding(8).background(DearbyStyle.mint, in: RoundedRectangle(cornerRadius: 8))
            Text(ActivityText.title(activity)).font(.title.bold())
            HStack {
                Text(state.catalog?.organizations.first { $0.id == activity.organizationId }?.name ?? "조직 미확인")
                    .font(.headline)
                Spacer()
                if showsSaving {
                Button(state.local.organizationIDs.contains(activity.organizationId) ? "조직 저장됨 · 해제" : "조직 저장",
                       systemImage: "bookmark") { mutate { try state.toggleOrganization(activity.organizationId) } }
                    .font(.caption).frame(minHeight: 44)
                }
            }
            Divider()
            detailField("참가 대상", activity.audience ?? "미확인", icon: "person.2")
            detailField("참가비", activity.cost ?? "미확인", icon: "creditcard")
            detailField("등록 마감", ActivityText.shortDate(activity.recruitmentEndAt), icon: "calendar")
            detailField("개최 일시", activity.dateLabel.isEmpty ? "미확인" : activity.dateLabel, icon: "clock")
            detailField("장소", activity.location ?? "미확인", icon: "mappin.and.ellipse")
            Divider()
            Text("소개").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.summary).lineSpacing(5)
        }
    }
    private func detailField(_ title: String, _ value: String, icon: String) -> some View {
        let layout = dynamicTypeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 6))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 14))
        return layout {
            Label { Text(title) } icon: { Image(systemName: icon).frame(width: 22) }
                .foregroundStyle(DearbyStyle.quiet).frame(minWidth: 108, alignment: .leading)
            Text(value).frame(maxWidth: .infinity, alignment: .leading)
        }.font(.subheadline)
    }
    private func applicationActions(_ activity: ActivityModel) -> some View {
        VStack(spacing: 0) {
            Divider()
            HStack(spacing: 12) {
                if showsSaving {
                Button {
                    mutate { try state.toggleProgram(activity.programId) }
                } label: {
                    Image(systemName: state.local.programIDs.contains(activity.programId) ? "bookmark.fill" : "bookmark")
                        .font(.title2).frame(width: 50, height: 50)
                        .overlay(RoundedRectangle(cornerRadius: 11).stroke(DearbyStyle.line))
                }.accessibilityLabel(state.local.programIDs.contains(activity.programId) ? "프로그램 저장됨 · 해제" : "프로그램 저장")
                }
                Button(applied ? "공식 사이트에서 확인하기" : "공식 사이트에서 신청") {
                    open(applied ? activity.officialUrl : activity.applicationUrl, application: !applied)
                }.buttonStyle(DearbyButtonStyle())
                    .accessibilityLabel(applied ? "공식 사이트에서 확인하기" : "신청 페이지 열기")
                    .disabled(ActivityModel.safeURL(applied ? activity.officialUrl : activity.applicationUrl) == nil)
            }.padding(.horizontal, 20).padding(.vertical, 12)
        }.background(.white)
    }
    private func source(_ activity: ActivityModel) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Divider()
            Text("정보 출처").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            if let url = ActivityModel.safeURL(activity.officialUrl) {
                Button("공식 사이트 보기", systemImage: "arrow.up.right") { open(activity.officialUrl, application: false) }
                    .frame(minHeight: 44)
                Text(url.host ?? "").font(.caption).foregroundStyle(DearbyStyle.quiet)
            } else { Text("공식 출처 링크 미확인") }
            Text("\(activity.isPreview == true ? "목 데이터 생성" : "공식 출처 확인"): \(ActivityText.date(activity.sourceCheckedAt))").font(.footnote)
            if !activity.sourceNote.isEmpty { Text(activity.sourceNote).font(.footnote).foregroundStyle(DearbyStyle.quiet) }
            Text("공식 페이지에서 로그인·동의·최종 제출을 직접 진행하세요. 자동입력은 아직 지원하지 않아요.")
                .font(.caption).foregroundStyle(DearbyStyle.quiet)
        }
    }
    private var applicationBanner: some View {
        Label("이 활동은 이미 신청한 활동이에요.", systemImage: "checkmark.circle.fill")
            .font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal).padding(.vertical, 8)
            .background(Color(red: 0.9, green: 0.97, blue: 0.95), ignoresSafeAreaEdges: [])
    }
    private func open(_ raw: String?, application: Bool) {
        guard let url = ActivityModel.safeURL(raw) else { return }
        applicationAttempt = application
        browser = ActivityBrowserDestination(url: url)
    }
    private func report(_ status: ActivityLibraryModel.ApplicationStatus) {
        mutate { try state.report(status, activityID: activityID) }
    }
    private func mutate(_ action: () throws -> Void) {
        do { try action() } catch { message = error.localizedDescription }
    }
}
private struct ActivityBrowserDestination: Identifiable {
    let id = UUID()
    let url: URL
}
