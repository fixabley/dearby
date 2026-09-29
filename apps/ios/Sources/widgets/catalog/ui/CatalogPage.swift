import SwiftUI

struct CatalogPage: View {
    let state: CatalogState
    let saved: Bool
    var showsSaving = true
    @State private var message: String?
    @State private var filter = 0
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    var body: some View {
        TimelineView(.explicit(state.expirationDates)) { timeline in
            Group {
                if saved {
                    List { connection; savedSections(at: timeline.date) }.listStyle(.plain)
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            DearbyLogo(width: 96).padding(.bottom, 10)
                            HStack(spacing: 10) {
                                Text("모집 중인 활동").font(.title2.bold())
                                if state.catalog?.activities.contains(where: { $0.isPreview == true }) == true {
                                    Text("예시").font(.caption).foregroundStyle(DearbyStyle.quiet)
                                        .padding(.horizontal, 10).padding(.vertical, 5)
                                        .background(DearbyStyle.muted, in: Capsule())
                                }
                            }
                            ScrollView(.horizontal) {
                                HStack(spacing: 8) {
                                    ForEach(Array(["전체", "참가등록형", "선발형"].enumerated()), id: \.offset) { index, title in
                                        Button { filter = index } label: {
                                            Text(title).font(.subheadline.weight(.semibold)).padding(.horizontal, 20).frame(minHeight: 44)
                                                .background(filter == index ? DearbyStyle.teal : DearbyStyle.muted, in: Capsule())
                                                .foregroundStyle(filter == index ? .white : DearbyStyle.quiet)
                                        }.buttonStyle(.plain).accessibilityAddTraits(filter == index ? .isSelected : [])
                                    }
                                }
                            }.scrollIndicators(.hidden)
                            discovery(at: timeline.date)
                            connection
                        }.padding(20)
                    }
                }
            }
            .navigationDestination(for: String.self) { id in
                ActivityDetailView(state: state, activityID: id, showsSaving: showsSaving)
            }
        }
        .scrollContentBackground(.hidden).background(.white)
        .tint(DearbyStyle.teal).navigationTitle(saved ? "저장" : "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(saved ? .visible : .hidden, for: .navigationBar)
        .refreshable { await state.refresh() }
        .alert("저장하지 못했어요", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("확인") { message = nil }
        } message: { Text(message ?? "") }
    }
    @ViewBuilder private var connection: some View {
        if state.loading { ProgressView("활동을 불러오는 중…") }
        if let error = state.error {
            Section {
                Label(error, systemImage: "exclamationmark.arrow.trianglehead.2.clockwise.rotate.90")
                    .font(.footnote)
                Button("다시 불러오기") { Task { await state.refresh() } }.disabled(state.loading)
            }
        }
        if state.fromCache {
            Text("기기에 보관한 정보 · 만료된 활동은 발견에 표시하지 않아요.").font(.footnote).foregroundStyle(DearbyStyle.quiet)
        }
        if let fetched = state.fetchedAt {
            Text("마지막 수신 \(ActivityText.date(fetched))")
                .font(.caption).foregroundStyle(DearbyStyle.quiet)
        }
    }
    @ViewBuilder private func discovery(at now: Date) -> some View {
        if let catalog = state.catalog {
            let activities = catalog.activities.filter { $0.isOpen(at: now) && (filter == 0 || (filter == 1 ? $0.participationType == .registration : $0.participationType == .selection)) }
            if activities.isEmpty {
                ContentUnavailableView("확인된 모집 중 활동이 없어요", systemImage: "safari",
                    description: Text(state.fromCache ? "보관한 정보만으로 현재 모집을 확인할 수 없어요. 다시 불러와 주세요."
                        : "공식 출처에서 모집 여부와 최신성이 확인된 활동이 표시됩니다."))
            } else {
                ForEach(activities) { row($0, at: now) }
            }
        } else if !state.loading && state.error == nil {
            Button("활동 불러오기") { Task { await state.refresh() } }
        }
    }
    @ViewBuilder private func savedSections(at now: Date) -> some View {
        Section {
            Text("프로그램과 조직을 이 기기에 저장해요. 계정 동기화나 모집 알림은 제공하지 않아요.")
                .font(.footnote).foregroundStyle(DearbyStyle.quiet)
        }
        if state.local.programIDs.isEmpty && state.local.organizationIDs.isEmpty {
            ContentUnavailableView("저장한 활동", systemImage: "bookmark",
                description: Text("활동 상세에서 관심 있는 프로그램과 조직을 저장해 보세요."))
        }
        ForEach(state.local.programIDs.sorted(), id: \.self) { id in
            Section {
                let activities = state.catalog?.activities.filter { $0.programId == id } ?? []
                savedRows(activities, at: now)
                Button("프로그램 저장 해제") { mutate { try state.toggleProgram(id) } }
            } header: { Text(state.catalog?.programs.first { $0.id == id }?.title ?? "저장한 프로그램 · 정보 미수신") }
        }
        ForEach(state.local.organizationIDs.sorted(), id: \.self) { id in
            Section {
                let activities = state.catalog?.activities.filter { $0.organizationId == id } ?? []
                savedRows(activities, at: now)
                Button("조직 저장 해제") { mutate { try state.toggleOrganization(id) } }
            } header: { Text(state.catalog?.organizations.first { $0.id == id }?.name ?? "저장한 조직 · 정보 미수신") }
        }
    }
    @ViewBuilder private func savedRows(_ activities: [ActivityModel], at now: Date) -> some View {
        if activities.isEmpty { Text("표시할 활동 정보가 없어요. 저장은 유지됩니다.").font(.footnote) }
        ForEach(activities) { row($0, at: now) }
    }
    private func row(_ activity: ActivityModel, at now: Date) -> some View {
        HStack(alignment: .top, spacing: 12) {
            NavigationLink(value: activity.id) {
                HStack(alignment: .top, spacing: 12) {
                    if !dynamicTypeSize.isAccessibilitySize {
                        ActivityArtwork(url: activity.imageUrl, preview: activity.isPreview == true)
                            .frame(width: 112, height: 116).clipShape(RoundedRectangle(cornerRadius: 9))
                    }
                    VStack(alignment: .leading, spacing: 7) {
                        Text(ActivityText.title(activity)).font(.headline).foregroundStyle(Color.primary)
                        Text(activity.audience ?? activity.summary).font(.subheadline).foregroundStyle(DearbyStyle.quiet).lineLimit(2)
                        Divider()
                        Label("마감 " + ActivityText.shortDate(activity.recruitmentEndAt), systemImage: "calendar")
                        Label(activity.dateLabel.isEmpty ? "일정 미확인" : activity.dateLabel, systemImage: "mappin.and.ellipse")
                        if saved { Text(activity.status(at: now)).foregroundStyle(DearbyStyle.teal) }
                    }.font(.caption).foregroundStyle(DearbyStyle.quiet).frame(minHeight: 116).frame(maxWidth: .infinity, alignment: .leading)
                }
            }.buttonStyle(.plain).accessibilityIdentifier("activity-\(activity.id)")
            if showsSaving {
            Button {
                mutate { try state.toggleProgram(activity.programId) }
            } label: {
                Image(systemName: state.local.programIDs.contains(activity.programId) ? "bookmark.fill" : "bookmark")
                    .font(.title3).frame(width: 44, height: 44)
            }.buttonStyle(.plain).foregroundStyle(DearbyStyle.teal)
                .accessibilityLabel(state.local.programIDs.contains(activity.programId) ? "프로그램 저장됨 · 해제" : "프로그램 저장")
            }
        }.padding(10).background(.white, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
    }
    private func mutate(_ action: () throws -> Void) {
        do { try action() } catch { message = error.localizedDescription }
    }
}

// One image treatment shared by discovery thumbnails and the detail hero.
struct ActivityArtwork: View {
    let url: String?
    let preview: Bool
    var body: some View {
        GeometryReader { geometry in
            AsyncImage(url: ActivityModel.safeURL(url)) { image in
                image.resizable().scaledToFit().frame(width: geometry.size.width, height: geometry.size.height)
            } placeholder: {
                VStack(spacing: 10) {
                    Image(systemName: preview ? "calendar.badge.clock" : "photo").font(.largeTitle)
                    Text(preview ? "DEARBY DEMO" : "이미지 미제공").font(.caption2.weight(.medium))
                }.foregroundStyle(DearbyStyle.teal).frame(width: geometry.size.width, height: geometry.size.height)
            }
        }.background(DearbyStyle.muted).accessibilityHidden(true)
    }
}
