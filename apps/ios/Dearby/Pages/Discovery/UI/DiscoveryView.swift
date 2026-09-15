import SwiftUI

struct DiscoveryView<Destination: View>: View {
    @Environment(\.dynamicTypeSize) private var typeSize

    let snapshotDate: String
    let cards: [NoticeCardState]
    let saveOrganization: (String) -> SaveOrganizationResult
    @ViewBuilder let destination: (String) -> Destination
    @State private var detail: NoticeCardState?
    @State private var saveFeedback = ""
    @State private var saveCount = 0

    var body: some View {
        VStack(spacing: 0) {
            Text("검토한 공고 샘플 · \(snapshotDate.prefix(10))")
                .font(.caption).foregroundStyle(.secondary)
                .padding(.bottom, 8)
            if cards.isEmpty {
                ContentUnavailableView("표시할 공고가 없어요", systemImage: "rectangle.stack")
            } else {
                GeometryReader { geometry in
                    ScrollView(.vertical) {
                        LazyVStack(spacing: 0) {
                            ForEach(Array(cards.enumerated()), id: \.element.id) { index, notice in
                                NoticeCard(
                                    state: notice,
                                    position: "\(index + 1) / \(cards.count)",
                                    compact: geometry.size.height < 520,
                                    onSave: { save(notice) },
                                    onShowDetail: { detail = notice }
                                )
                                .frame(width: geometry.size.width)
                                .frame(minHeight: geometry.size.height)
                                .frame(height: typeSize.isAccessibilitySize ? nil : geometry.size.height)
                            }
                        }
                        .scrollTargetLayout()
                    }
                    .scrollIndicators(.hidden)
                    .scrollTargetBehavior(DiscoveryPagingBehavior(enabled: !typeSize.isAccessibilitySize))
                }
            }
            Text(saveFeedback.isEmpty ? "위아래로 넘기기 · 더블탭으로 조직 저장" : saveFeedback)
                .font(.caption).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true).frame(minHeight: 36).padding(.horizontal)
                .accessibilityIdentifier("discovery.feedback")
        }
        .background(NativeSurface.canvas)
        .navigationTitle("활동 둘러보기")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $detail) { notice in
            destination(notice.id)
        }
        .sensoryFeedback(.success, trigger: saveCount)
    }

    private func save(_ notice: NoticeCardState) {
        switch saveOrganization(notice.id) {
        case .saved(let organization):
            saveFeedback = "\(organization) 저장됨"
            saveCount += 1
        case .unresolved:
            saveFeedback = "저장할 조직을 확인 중이에요"
        }
    }
}

/// Preserve native paging normally; let oversized accessibility cards scroll freely.
private struct DiscoveryPagingBehavior: ScrollTargetBehavior {
    let enabled: Bool

    func updateTarget(_ target: inout ScrollTarget, context: TargetContext) {
        if enabled {
            PagingScrollTargetBehavior().updateTarget(&target, context: context)
        }
    }
}
