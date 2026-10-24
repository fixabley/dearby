import SwiftUI

/// 한 화면에 질문 하나(contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24, 토스식 퍼널의 iOS 표현): 위 진행 막대(`ProgressView`), 질문은 첫 섹션
/// 머리(`.title2.bold()` + 보조 설명), 답은 inset-grouped `List`의 행, 아래 전체 너비 주 버튼(`.borderedProminent` + `.large`). 뒤로는 시스템 내비게이션.
/// `content`는 `List` 안의 섹션·행이다.
struct DearbyQuestionPage<Content: View>: View {
    /// 0...1.
    let progress: Double
    let title: String
    var message: String?
    let button: String
    var disabled = false
    var busy = false
    let next: () -> Void
    @ViewBuilder let content: Content
    var body: some View {
        List {
            Section {} header: {
                VStack(alignment: .leading, spacing: 6) {
                    Text(title).font(.dearby(.title2).bold()).foregroundStyle(.primary).accessibilityAddTraits(.isHeader)
                    if let message { Text(message).font(.dearby(.body)).foregroundStyle(.secondary) }
                }.textCase(nil).padding(.top, 12)
            }
            content
        }
        .font(.dearby(.body))
        .safeAreaInset(edge: .top, spacing: 0) {
            ProgressView(value: progress).tint(DearbyStyle.teal).padding(.horizontal, 20).padding(.vertical, 8)
                .accessibilityLabel("진행").accessibilityValue("\(Int((progress * 100).rounded()))%")
        }
        .safeAreaInset(edge: .bottom) {
            Button(action: next) {
                Group { if busy { ProgressView().tint(DearbyStyle.onTeal) } else { Text(button) } }.frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent).controlSize(.large).tint(DearbyStyle.tealFill).font(.dearby(.body).weight(.semibold))
            .disabled(disabled || busy)
            .padding(.horizontal, 20).padding(.vertical, 12).background(.bar)
        }
        .background(Color(.systemGroupedBackground))
        .navigationBarTitleDisplayMode(.inline)
    }
}
