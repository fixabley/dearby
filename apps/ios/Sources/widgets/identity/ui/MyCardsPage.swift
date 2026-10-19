import SwiftUI

/// 내 명함: the account's published cards. Pick one to view it, show its QR, make a new one, edit it in place with "이 명함 고치기", or delete it.
/// Signed out, empty and failed states are one centred message (contract "비스크롤 화면 배치·CTA"). Shown as the 내 명함 section of the 명함 tab
/// (`CardsPage`, contract "명함 탭 통합"), which owns the navigation bar.
struct MyCardsPage: View {
    let state: MyCardsModel
    @State private var editor = false
    /// 이 명함 고치기: the composer prefilled from this card saves over it (`PUT /v1/cards/:id`); `editor` (새 명함) never gets a prefill.
    /// Reloading on dismiss keeps the same id selected (`MyCardsModel.load`).
    @State private var editingCard: PublishedCard?
    @State private var signingIn = false
    /// 이 명함 지우기: the card awaiting confirmation; the server removes it and its public link.
    @State private var deletingCard: PublishedCard?
    @State private var deleteFailed = false
    /// QR 보이기 for the selected card (contract "명함 교환").
    @State private var showingQR: PublishedCard?
    @Environment(\.openURL) private var openURL
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    var body: some View {
        Group {
            switch state.phase {
            case .signedOut:
                CenteredMessage(symbol: "person.text.rectangle", title: "명함은 계정에 저장돼요", message: "로그인하면 만든 명함을 볼 수 있어요.",
                                primary: MessageAction("로그인") { signingIn = true })
            case .noCard:
                CenteredMessage(symbol: "person.text.rectangle", title: "첫 명함을 만들어 보세요",
                                message: "프로필의 연락처와 이력 중 보여 줄 것만 골라 발행해요. 발행할 때 패스키로 로그인해요.",
                                primary: MessageAction("명함 만들기") { editor = true })
            case .loading:
                ProgressView("명함을 불러오는 중이에요").frame(maxWidth: .infinity, maxHeight: .infinity).tint(DearbyStyle.teal)
            case .failed:
                CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "명함을 불러오지 못했어요", message: "연결을 확인하고 다시 시도해 주세요.",
                                primary: MessageAction("다시 시도") { Task { await state.load() } })
            case .loaded:
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) { cards }.padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 20)
                }.refreshable { await state.load() }
            }
        }.task { await state.load() }
            .sheet(isPresented: $editor, onDismiss: { Task { await state.load() } }) {
                NavigationStack { CardComposerPage(account: state.account) }
            }
            .sheet(item: $editingCard, onDismiss: { Task { await state.load() } }) { card in
                NavigationStack { CardComposerPage(account: state.account, prefill: card) }
            }
            .sheet(isPresented: $signingIn) {
                NavigationStack { SignInSheet(account: state.account) { Task { await state.load() } } }
            }
            .sheet(item: $showingQR) { card in
                NavigationStack { CardQRSheet(cardName: card.name, cardID: card.id, webOrigin: state.webOrigin, activities: state.appliedActivities()) }
            }
            .confirmationDialog("이 명함을 지울까요?", isPresented: Binding(get: { deletingCard != nil }, set: { if !$0 { deletingCard = nil } }),
                                titleVisibility: .visible, presenting: deletingCard) { card in
                Button("지우기", role: .destructive) { Task { deleteFailed = !(await state.delete(card.id)) } }
                Button("취소", role: .cancel) {}
            } message: { _ in Text("공개 링크도 더 열리지 않아요. 되돌릴 수 없어요.") }
            .alert("명함을 지우지 못했어요", isPresented: $deleteFailed) { Button("확인") {} } message: { Text("연결을 확인하고 다시 시도해 주세요.") }
    }
    /// 2-column grid of the account's cards, newest first, behind a "+ 새 명함" tile; the chosen card renders below.
    @ViewBuilder private var cards: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: dynamicTypeSize.isAccessibilitySize ? 1 : 2), spacing: 10) {
            tile(selected: false) { editor = true } label: {
                Label("새 명함", systemImage: "plus").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
            ForEach(state.cards.reversed()) { item in
                tile(selected: item.id == state.selectedCardID) { state.select(item.id) } label: {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.name).font(.dearby(.subheadline).weight(.bold)).foregroundStyle(DearbyStyle.ink).lineLimit(1)
                        Text(item.profileName).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet).lineLimit(1)
                        if !item.job.isEmpty { Text(item.job).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet).lineLimit(1) }
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        if let card = state.card {
            CardView(card: card.cardModel) { if let url = $0.actionURL { openURL(url) } }
            Button { showingQR = card } label: {
                Label("QR 보이기", systemImage: "qrcode").frame(maxWidth: .infinity)
            }.buttonStyle(.borderedProminent).controlSize(.large).tint(DearbyStyle.tealFill).font(.dearby(.body).weight(.semibold))
                .accessibilityHint("상대가 카메라로 읽어 이 명함을 받아요")
            VStack(spacing: 2) {
                Button { editingCard = card } label: {
                    Label("이 명함 고치기", systemImage: "pencil").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                        .frame(maxWidth: .infinity, minHeight: 44).contentShape(Rectangle())
                }.accessibilityHint("이 명함의 이름과 공개 선택을 고쳐 저장해요")
                Text("고친 내용은 이 명함에 바로 반영돼요. QR과 링크는 그대로예요.").font(.dearby(.footnote)).foregroundStyle(.secondary)
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity)
            }
            Button(role: .destructive) { deletingCard = card } label: {
                Label("이 명함 지우기", systemImage: "trash").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.danger)
                    .frame(maxWidth: .infinity, minHeight: 44).contentShape(Rectangle())
            }.accessibilityHint("확인 뒤 명함과 공개 링크를 지워요")
        }
    }
    /// Grid tiles are the one place the page separates several items from each other, so they keep a surface; the selected one is tinted.
    private func tile(selected: Bool, action: @escaping () -> Void, @ViewBuilder label: () -> some View) -> some View {
        Button(action: action) {
            label().padding(14).frame(maxWidth: .infinity, minHeight: 84, alignment: .leading)
                .background(selected ? DearbyStyle.mint : Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(selected ? DearbyStyle.teal : .clear, lineWidth: 1.5))
                .contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityAddTraits(selected ? .isSelected : [])
    }
}
