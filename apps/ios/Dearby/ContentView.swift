import SwiftUI

struct ContentView: View {
    @State private var favorites = FavoriteOrganizations()
    @State private var catalog: ActivityCatalog?
    @State private var loadFailed = false

    var body: some View {
        Group {
            if let catalog {
                TabView {
                    Tab("발견", systemImage: "rectangle.stack") {
                        NavigationStack {
                            DiscoveryView(catalog: catalog, favorites: favorites)
                        }
                    }
                    Tab("즐겨찾기", systemImage: "heart") {
                        NavigationStack {
                            FavoriteListView(catalog: catalog, favorites: favorites)
                        }
                    }
                }
            } else if loadFailed {
                ContentUnavailableView {
                    Label("공고를 불러오지 못했어요", systemImage: "exclamationmark.triangle")
                } description: {
                    Text("앱을 다시 실행해 주세요.")
                } actions: {
                    Button("다시 시도", action: loadCatalog)
                }
            } else {
                ProgressView("공고 불러오는 중")
            }
        }
        .task { loadCatalog() }
    }

    private func loadCatalog() {
        do {
            catalog = try ActivityCatalog.load()
            loadFailed = false
        } catch {
            loadFailed = true
        }
    }
}

private struct DiscoveryView: View {
    let catalog: ActivityCatalog
    let favorites: FavoriteOrganizations
    @State private var detail: ActivityNotice?
    @State private var saveFeedback = ""
    @State private var saveCount = 0

    var body: some View {
        VStack(spacing: 0) {
            Text("검토한 공고 샘플 · \(catalog.snapshotAt.prefix(10))")
                .font(.caption).foregroundStyle(.secondary)
                .padding(.bottom, 8)
            if catalog.feed.isEmpty {
                ContentUnavailableView("표시할 공고가 없어요", systemImage: "rectangle.stack")
            } else {
                GeometryReader { geometry in
                    ScrollView(.vertical) {
                        LazyVStack(spacing: 0) {
                            ForEach(Array(catalog.feed.enumerated()), id: \.element.id) { index, notice in
                                ActivityCard(
                                    notice: notice,
                                    organization: catalog.organization(notice.favoriteOrganizationId),
                                    contextNames: catalog.contextNames(for: notice),
                                    saved: notice.favoriteOrganizationId.map { favorites.ids.contains($0) } ?? false,
                                    position: "\(index + 1) / \(catalog.feed.count)",
                                    compact: geometry.size.height < 520,
                                    save: { save(notice) },
                                    showDetail: { detail = notice }
                                )
                                .frame(width: geometry.size.width, height: geometry.size.height)
                            }
                        }
                        .scrollTargetLayout()
                    }
                    .scrollIndicators(.hidden)
                    .scrollTargetBehavior(.paging)
                }
            }
            Text(saveFeedback.isEmpty ? "위아래로 넘기기 · 더블탭으로 조직 저장" : saveFeedback)
                .font(.caption).foregroundStyle(.secondary)
                .lineLimit(2).frame(minHeight: 36).padding(.horizontal)
                .accessibilityIdentifier("discovery.feedback")
        }
        .navigationTitle("활동 둘러보기")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $detail) { notice in
            NoticeDetailView(notice: notice, catalog: catalog)
        }
        .sensoryFeedback(.success, trigger: saveCount)
    }

    private func save(_ notice: ActivityNotice) {
        guard let organization = catalog.organization(notice.favoriteOrganizationId) else {
            saveFeedback = "저장할 조직을 확인 중이에요"
            return
        }
        favorites.save(organization.id)
        saveFeedback = "\(organization.name) 저장됨"
        saveCount += 1
    }
}

private struct ActivityCard: View {
    let notice: ActivityNotice
    let organization: ActivityOrganization?
    let contextNames: String
    let saved: Bool
    let position: String
    let compact: Bool
    let save: () -> Void
    let showDetail: () -> Void
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(alignment: .leading, spacing: compact ? 10 : 18) {
            VStack(alignment: .leading, spacing: compact ? 10 : 18) {
                HStack {
                    Label("공고 샘플", systemImage: "sparkle")
                        .foregroundStyle(.tint)
                    Spacer()
                    Text(position).foregroundStyle(.secondary)
                }.font(.caption.bold())
                Text([notice.categorySummary, contextNames].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary).lineLimit(2)
                    .accessibilityIdentifier("classification.\(notice.id)")
                Text(notice.title)
                    .font(compact ? .title3.bold() : .title2.bold())
                    .lineLimit(3)
                if !compact && !typeSize.isAccessibilitySize {
                    Divider()
                    NoticeFact(label: "참여 대상", value: notice.audience.summary)
                    NoticeFact(label: "신청 마감", value: notice.application.summary)
                    NoticeFact(label: "활동 장소", value: notice.location.summary)
                }
                if !notice.qualityIssues.isEmpty {
                    Label("확인이 필요한 정보가 있어요", systemImage: "info.circle")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .contentShape(Rectangle())
            .onTapGesture(count: 2, perform: save)
            .accessibilityAction(named: "조직 즐겨찾기에 저장", save)
            .accessibilityIdentifier("activity.\(notice.id)")

            if let organization {
                Button(action: save) {
                    Label(saved ? "저장됨 · \(organization.name)" : "\(organization.name) 저장",
                          systemImage: saved ? "heart.fill" : "heart")
                        .frame(maxWidth: .infinity).lineLimit(2)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("save.\(notice.id)")
            } else {
                Text("저장할 조직 확인 중").font(.caption).foregroundStyle(.secondary)
            }
            Button("공고 정보 · 출처 보기", action: showDetail)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("details.\(notice.id)")
        }
        .padding(compact ? 16 : 22)
        .background(.background, in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(.quaternary))
        .padding(.horizontal, 16).padding(.vertical, 8)
    }
}

private struct NoticeFact: View {
    let label: String
    let value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.subheadline).lineLimit(2)
        }
    }
}

private struct FavoriteListView: View {
    let catalog: ActivityCatalog
    let favorites: FavoriteOrganizations

    private var savedOrganizations: [ActivityOrganization] {
        catalog.organizations.filter { favorites.ids.contains($0.id) }
    }

    var body: some View {
        Group {
            if savedOrganizations.isEmpty {
                ContentUnavailableView("저장한 조직이 없어요", systemImage: "heart",
                                       description: Text("발견 탭의 공고를 더블탭하면 조직이 여기에 저장돼요."))
            } else {
                List(savedOrganizations) { organization in
                    VStack(alignment: .leading, spacing: 10) {
                        Text(organization.name).font(.headline)
                        let ancestors = catalog.organizationPath(organization.id).dropLast()
                        if !ancestors.isEmpty {
                            Text(ancestors.map(\.name).joined(separator: " › "))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        let notices = catalog.feed.filter { $0.favoriteOrganizationId == organization.id }
                        Text(notices.isEmpty ? "현재 연결된 공고가 없어요" : "연결된 공고 \(notices.count)개")
                            .font(.caption).foregroundStyle(.secondary)
                        ForEach(notices) { notice in
                            NavigationLink {
                                NoticeDetailView(notice: notice, catalog: catalog)
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(notice.title)
                                    Text([notice.categorySummary, catalog.contextNames(for: notice)]
                                        .filter { !$0.isEmpty }.joined(separator: " · "))
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                        Button("즐겨찾기에서 삭제", role: .destructive) {
                            favorites.remove(organization.id)
                        }
                        .font(.caption)
                        .accessibilityIdentifier("remove.\(organization.id)")
                    }.padding(.vertical, 6)
                }
            }
        }
        .navigationTitle("즐겨찾기")
        .safeAreaInset(edge: .bottom) {
            Text("이 기기에 저장돼요").font(.caption).foregroundStyle(.secondary).padding(8)
        }
    }
}

private struct NoticeDetailView: View {
    let notice: ActivityNotice
    let catalog: ActivityCatalog

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(notice.title).font(.title2.bold())
                Text(notice.summary)
                NoticeIdentityView(notice: notice, catalog: catalog)
                Divider()
                detail("참여 대상", notice.audience.summary)
                detail("참여 조건", notice.eligibility.summary)
                detail("신청 기간", notice.application.summary)
                ForEach(Array(notice.schedule.enumerated()), id: \.offset) { _, phase in
                    detail("활동 일정", phase.summary)
                }
                detail("활동 장소", notice.location.summary)
                ForEach(Array(notice.benefits.enumerated()), id: \.offset) { _, benefit in
                    detail("혜택", benefit.summary)
                }
                ForEach(Array(notice.qualityIssues.enumerated()), id: \.offset) { _, issue in
                    detail("확인 필요", issue.summary)
                }
                Text("원문을 검토해 만든 샘플입니다. 현재 모집 여부와 변경된 조건은 원문에서 확인해 주세요.")
                    .font(.footnote).foregroundStyle(.secondary)
                if let sourceURL = catalog.sourceURL(for: notice) {
                    Link("원문 공고 열기", destination: sourceURL)
                }
            }.frame(maxWidth: 620, alignment: .leading).padding(24)
        }
        .navigationTitle("공고 정보")
        .presentationDragIndicator(.visible)
    }

    private func detail(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value)
        }
    }
}

#Preview {
    ContentView()
}
