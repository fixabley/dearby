import SwiftUI

extension View {
    /// 내용을 불러오는 동안의 자리 표시(contract "내용 로딩은 스켈레톤" 2026-10-22): 예시 값을 넣은 실제 행·카드에 시스템 자리 표시자
    /// (`.redacted(reason: .placeholder)`)를 씌운다. 묶음 전체가 `불러오는 중` 한 요소로 읽히고 자리 표시 글자는 읽히지 않으며, 누를 수 없다.
    /// 은은한 맥박은 동작 줄이기에서 멈춘다.
    func dearbySkeleton() -> some View { modifier(SkeletonModifier()) }
}

private struct SkeletonModifier: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func body(content: Content) -> some View {
        Group {
            if reduceMotion {
                content
            } else {
                content.phaseAnimator([1, 0.5]) { $0.opacity($1) } animation: { _ in .easeInOut(duration: 0.9) }
            }
        }
        .redacted(reason: .placeholder).allowsHitTesting(false)
        .accessibilityElement(children: .ignore).accessibilityLabel("불러오는 중")
    }
}

/// 목록 안 두 줄 행(이름 + 보조 한 줄) 자리 표시: 검색 결과·차단 목록·변경 이력처럼 행 모양이 같은 곳에서 한 목록 행으로 넣는다.
/// 여러 행이어도 하나의 `불러오는 중` 요소가 되도록 구분선으로 나눈 한 묶음이다.
struct DearbyRowsSkeleton: View {
    var count = 3
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(0..<count, id: \.self) { index in
                if index > 0 { Divider() }
                VStack(alignment: .leading, spacing: 2) {
                    Text("불러오는 항목 이름")
                    Text("함께 보이는 설명 한 줄").font(.dearby(.footnote)).foregroundStyle(.secondary)
                }.frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).padding(.vertical, 6)
            }
        }
        .dearbySkeleton()
    }
}
