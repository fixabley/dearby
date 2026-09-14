import SwiftUI

struct DiscoveryView<Destination: View>: View {
    let catalog: ActivityCatalog
    let favoriteIDs: Set<String>
    let saveOrganization: (String) -> Void
    @ViewBuilder let destination: (ActivityNotice) -> Destination
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
                                    saved: notice.favoriteOrganizationId.map { favoriteIDs.contains($0) } ?? false,
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
            destination(notice)
        }
        .sensoryFeedback(.success, trigger: saveCount)
    }

    private func save(_ notice: ActivityNotice) {
        guard let organization = catalog.organization(notice.favoriteOrganizationId) else {
            saveFeedback = "저장할 조직을 확인 중이에요"
            return
        }
        saveOrganization(organization.id)
        saveFeedback = "\(organization.name) 저장됨"
        saveCount += 1
    }
}
