import SwiftUI

struct DiscoveryView<Destination: View>: View {
    @Environment(\.dynamicTypeSize) private var typeSize

    let snapshotDate: String
    private let cards: [NoticeCardViewModel]
    @ViewBuilder let destination: (String) -> Destination
    @State private var focusedID: String?
    @State private var detail: NoticeCardState?
    @State private var saveFeedback = ""
    @State private var saveCount = 0

    init(snapshotDate: String, viewModels: [NoticeCardViewModel], @ViewBuilder destination: @escaping (String) -> Destination) {
        self.snapshotDate = snapshotDate
        cards = viewModels.filter(\.isDisplayable)
        self.destination = destination
    }

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
                                DiscoveryCardPage(viewModel: notice, position: "\(index + 1) / \(cards.count)",
                                    viewport: geometry.size, scrollContents: typeSize.isAccessibilitySize || geometry.size.height < 520,
                                    onSaved: saved, onShowDetail: { detail = notice.state })
                                    .id(notice.id)
                            }
                        }
                        .scrollTargetLayout()
                    }
                    .scrollIndicators(.hidden)
                    .scrollTargetBehavior(.viewAligned(limitBehavior: .alwaysByOne))
                    .scrollPosition(id: $focusedID)
                }
            }
            if typeSize.isAccessibilitySize, !cards.isEmpty {
                let index = cards.firstIndex { $0.id == focusedID } ?? 0
                DiscoveryPageControls(position: "\(index + 1) / \(cards.count)",
                    canPrevious: index > 0, canNext: index + 1 < cards.count,
                    onPrevious: { withAnimation { focusedID = cards[max(0, index - 1)].id } },
                    onNext: { withAnimation { focusedID = cards[min(cards.count - 1, index + 1)].id } })
            }
            Text(saveFeedback.isEmpty ? "위아래로 넘기기 · 더블탭으로 조직 저장" : saveFeedback)
                .font(.caption).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true).frame(minHeight: 36).padding(.horizontal)
                .accessibilityIdentifier("discovery.feedback")
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("활동 둘러보기")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $detail) { notice in
            destination(notice.id)
        }
        .sensoryFeedback(.success, trigger: saveCount)
    }

    private func saved(_ result: SaveOrganizationResult) {
        switch result {
        case .saved(let organization):
            saveFeedback = "\(organization) 저장됨"
            saveCount += 1
        case .unresolved:
            saveFeedback = "저장할 조직을 확인 중이에요"
        }
    }
}
