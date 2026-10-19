import SwiftUI

/// 비스크롤 화면의 공통 배치(contract "비스크롤 화면 배치·CTA" 2026-10-19 밤): 상태 심볼·제목·설명을 화면(시트) 가운데에 세로·가로 중앙 정렬하고,
/// 행동은 아래쪽에 전체 너비 주 버튼(`.borderedProminent` + `.controlSize(.large)`, 청록 채움 `tealFill`)과 그 아래 텍스트 보조 버튼(`.borderless`)으로 둔다.
/// 카드·테두리 없이 배경 위에 바로 그리고, 글자 계층은 한 가지(제목 `.title2.bold()`, 설명 `.body` 보조색)만 쓴다.
/// 내용이 넘치면 가운데 정렬을 유지한 채 스크롤이 생기고, 버튼 묶음은 안전 영역 아래 여백 16으로 고정된다.
/// 빈 상태·오류·로그인 시트·소개·QR·신청 확인처럼 글자 계층이 어긋나기 쉬운 화면은 모두 이 뷰로 그린다.
struct CenteredMessage<Glyph: View, Actions: View>: View {
    let title: String
    var message: String?
    /// 발견 탭처럼 페이지가 자기 `ScrollView`(풀투 리프레시·접히는 검색 영역)를 이미 가진 곳에 넣을 때: 스크롤 뷰를 따로 만들지 않고
    /// 가운데 정렬만 하며, 높이는 넣는 쪽이 `frame(minHeight:)`로 준다.
    var embedded = false
    @ViewBuilder let glyph: Glyph
    @ViewBuilder let actions: Actions

    var body: some View {
        if embedded {
            VStack(spacing: 0) {
                Spacer(minLength: 32)
                heading
                Spacer(minLength: 32)
                actionStack
            }.frame(maxWidth: .infinity)
        } else {
            VStack(spacing: 0) {
                GeometryReader { proxy in
                    ScrollView {
                        heading.frame(maxWidth: .infinity, minHeight: proxy.size.height)
                    }.scrollBounceBehavior(.basedOnSize)
                }
                actionStack
            }
        }
    }
    private var heading: some View {
        VStack(spacing: 12) {
            glyph.padding(.bottom, 8)
            Text(title).font(.dearby(.title2).bold()).foregroundStyle(.primary).multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            if let message, !message.isEmpty {
                Text(message).font(.dearby(.body)).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
        }.padding(.horizontal, 24).padding(.vertical, 16)
    }
    /// 주 버튼은 청록 채움 + 흰 글자, 보조 버튼은 텍스트. 둘 이상의 주 버튼은 세로로 쌓인다(가로 분할 없음).
    private var actionStack: some View {
        VStack(spacing: 8) { actions }
            .frame(maxWidth: 420).frame(maxWidth: .infinity)
            .controlSize(.large).font(.dearby(.body).weight(.semibold)).tint(DearbyStyle.tealFill)
            .padding(.horizontal, 24).padding(.bottom, 16)
    }
}

/// 상태 심볼(contract "아이콘 통일"): 청록 채움 원 64 안의 흰 SF Symbol 28(`.fill` 변형이 있으면 그것). 오류는 `danger` 채움.
struct StateGlyph: View {
    let symbol: String
    var danger = false
    var body: some View {
        Image(systemName: symbol).symbolVariant(.fill).font(.system(size: 28, weight: .semibold)).foregroundStyle(DearbyStyle.onTeal)
            .frame(width: 64, height: 64).background(danger ? DearbyStyle.danger : DearbyStyle.tealFill, in: Circle())
            .accessibilityHidden(true)
    }
}

/// 한 화면의 행동 하나: 제목과 동작. `role: .destructive`는 빨간 보조 버튼이 된다.
struct MessageAction {
    let title: String
    var role: ButtonRole?
    var identifier: String?
    let action: () -> Void
    init(_ title: String, role: ButtonRole? = nil, identifier: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.role = role
        self.identifier = identifier
        self.action = action
    }
}

/// 주 버튼 하나(전체 너비 채움)와 보조 텍스트 버튼 하나의 기본 조합.
struct MessageActions: View {
    var primary: MessageAction?
    var secondary: MessageAction?
    var body: some View {
        if let primary {
            Button(role: primary.role, action: primary.action) { Text(primary.title).frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).accessibilityIdentifier(primary.identifier ?? "")
        }
        if let secondary {
            Button(role: secondary.role, action: secondary.action) { Text(secondary.title).frame(maxWidth: .infinity, minHeight: 44) }
                .buttonStyle(.borderless).tint(secondary.role == .destructive ? DearbyStyle.danger : DearbyStyle.teal)
                .accessibilityIdentifier(secondary.identifier ?? "")
        }
    }
}

extension CenteredMessage where Glyph == StateGlyph {
    /// 상태 심볼 + 행동 묶음을 직접 그리는 꼴(로그인 시트처럼 버튼이 여럿일 때).
    init(symbol: String, danger: Bool = false, title: String, message: String? = nil, embedded: Bool = false, @ViewBuilder actions: () -> Actions) {
        self.init(title: title, message: message, embedded: embedded, glyph: { StateGlyph(symbol: symbol, danger: danger) }, actions: actions)
    }
}

extension CenteredMessage where Glyph == StateGlyph, Actions == MessageActions {
    /// 가장 흔한 꼴: 상태 심볼, 제목, 설명, 주 버튼 하나, 보조 텍스트 버튼 하나.
    init(symbol: String, danger: Bool = false, title: String, message: String? = nil, embedded: Bool = false,
         primary: MessageAction? = nil, secondary: MessageAction? = nil) {
        self.init(title: title, message: message, embedded: embedded, glyph: { StateGlyph(symbol: symbol, danger: danger) },
                  actions: { MessageActions(primary: primary, secondary: secondary) })
    }
}

#if DEBUG
#Preview("빈 상태") {
    CenteredMessage(symbol: "checklist", title: "신청한 활동이 없어요", message: "발견에서 관심 있는 활동을 신청해 보세요.",
                    primary: MessageAction("활동 둘러보기") {}, secondary: MessageAction("나중에") {})
        .background(Color(.systemGroupedBackground))
}

#Preview("오류") {
    CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "명함을 불러오지 못했어요", message: "연결을 확인하고 다시 시도해 주세요.",
                    primary: MessageAction("다시 시도") {})
        .background(Color(.systemGroupedBackground))
}
#endif
