import SwiftUI

struct ActivityDetailView: View {
    let state: CatalogState
    let activityID: String
    @State private var browser: ActivityBrowserDestination?
    @State private var applicationAttempt = false
    @State private var showReport = false
    @State private var message: String?
    private var activity: ActivityModel? { state.catalog?.activities.first { $0.id == activityID } }
    private var applied: Bool { state.local.applications[activityID] == .applied }
    var body: some View {
        Group {
            if let activity {
                TimelineView(.explicit(state.expirationDates)) { timeline in
                    List {
                        identity(activity, at: timeline.date)
                        ActivityInformationView(activity: activity)
                        bookmarks(activity)
                        source(activity)
                        Section("신청") {
                            if applied {
                                Button("공식 사이트에서 확인하기", systemImage: "safari") {
                                    open(activity.officialUrl, application: false)
                                }.frame(minHeight: 44).disabled(ActivityModel.safeURL(activity.officialUrl) == nil)
                                Text("직접 남긴 신청 기록이에요. 주최 측의 접수·선정·결제 확인과는 달라요.")
                                    .font(.footnote).foregroundStyle(.secondary)
                            } else if ActivityModel.safeURL(activity.applicationUrl) != nil {
                                Button("신청 페이지 열기", systemImage: "arrow.up.right.square") {
                                    open(activity.applicationUrl, application: true)
                                }.frame(minHeight: 44)
                                Text("공식 페이지에서 로그인·동의·최종 제출을 직접 진행하세요. 자동입력은 아직 지원하지 않아요.")
                                    .font(.footnote).foregroundStyle(.secondary)
                            } else { Text("신청 링크 미확인 · 공식 출처에서 확인해 주세요.") }
                            Button("신청 상태 수정") { showReport = true }.frame(minHeight: 44)
                            Text("신청 기록은 이 기기에만 저장됩니다.").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                .safeAreaInset(edge: .top, spacing: 0) {
                    if applied { applicationBanner }
                }
            } else {
                ContentUnavailableView("활동 정보를 확인할 수 없어요", systemImage: "doc.questionmark",
                    description: Text("새로고침에서 활동이 제외되었어요. 저장한 프로그램과 신청 기록은 유지됩니다."))
            }
        }
        .navigationTitle("활동 상세").navigationBarTitleDisplayMode(.inline)
        .listStyle(.insetGrouped).scrollContentBackground(.hidden).background(.white)
        .tint(Color(red: 0, green: 0.36, blue: 0.34))
        .sheet(item: $browser, onDismiss: {
            if applicationAttempt { applicationAttempt = false; showReport = true }
        }) { destination in ActivityBrowser(url: destination.url) }
        .confirmationDialog("신청을 완료하셨나요?", isPresented: $showReport, titleVisibility: .visible) {
            Button("신청했어요") { report(.applied) }
            Button("신청하지 않았어요") { report(.notApplied) }
            Button("나중에", role: .cancel) {}
        } message: { Text("직접 남기는 기록이에요. 주최 측의 접수·선정·결제 확인과는 달라요.") }
        .alert("저장하지 못했어요", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("확인") { message = nil }
        } message: { Text(message ?? "") }
    }
    private func identity(_ activity: ActivityModel, at now: Date) -> some View {
        Section {
            Text(activity.title).font(.title2.bold())
            Text(state.catalog?.organizations.first { $0.id == activity.organizationId }?.name ?? "조직 미확인")
                .font(.subheadline).foregroundStyle(.secondary)
            Text(activity.status(at: now)).font(.subheadline.bold())
            Text(activity.participationType == .selection ? "선발형 · 신청 후 주최 측 선정이 필요해요" : "참가등록형 · 등록 조건을 확인해 주세요")
                .font(.footnote)
            Text(activity.summary)
        }
    }
    private func bookmarks(_ activity: ActivityModel) -> some View {
        Section("관심 활동 저장") {
            Button(state.local.programIDs.contains(activity.programId) ? "프로그램 저장됨 · 해제" : "프로그램 저장",
                   systemImage: state.local.programIDs.contains(activity.programId) ? "bookmark.fill" : "bookmark") {
                mutate { try state.toggleProgram(activity.programId) }
            }.frame(minHeight: 44)
            Button(state.local.organizationIDs.contains(activity.organizationId) ? "조직 저장됨 · 해제" : "조직 저장",
                   systemImage: state.local.organizationIDs.contains(activity.organizationId) ? "bookmark.fill" : "bookmark") {
                mutate { try state.toggleOrganization(activity.organizationId) }
            }.frame(minHeight: 44)
        }
    }
    private func source(_ activity: ActivityModel) -> some View {
        Section("출처") {
            Text("공식 출처 확인: \(ActivityText.date(activity.sourceCheckedAt))").font(.footnote)
            Text("정보 유효 기한: \(ActivityText.date(activity.validUntil))").font(.footnote)
            if !activity.sourceNote.isEmpty { Text(activity.sourceNote).font(.footnote) }
            if let url = ActivityModel.safeURL(activity.officialUrl) {
                Text(url.host ?? "").font(.caption).foregroundStyle(.secondary)
                Button("공식 사이트 보기", systemImage: "safari") { open(activity.officialUrl, application: false) }
                    .frame(minHeight: 44)
            } else { Text("공식 출처 링크 미확인") }
        }
    }
    private var applicationBanner: some View {
        Label("이 활동은 이미 신청한 활동이에요.", systemImage: "checkmark.circle.fill")
            .font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal).padding(.vertical, 8)
            .background(Color(red: 0.9, green: 0.97, blue: 0.95))
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
