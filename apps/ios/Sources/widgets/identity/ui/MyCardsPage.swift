import SwiftUI

/// 내 명함: the account's published cards. Pick one to view it, make a new one, republish it with "이 명함 고치기", or delete it.
struct MyCardsPage: View {
    let state: MyCardsModel
    @State private var editor = false
    /// 명함 고치기: a new publication prefilled from this card's template (cards never change after publishing).
    @State private var editingCard: PublishedCard?
    @State private var signingIn = false
    /// 이 명함 지우기: the card awaiting confirmation; the server removes it and its public link.
    @State private var deletingCard: PublishedCard?
    @State private var deleteFailed = false
    @Environment(\.openURL) private var openURL
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                DearbyLogo(width: 80)
                Text("내 명함").font(.title.bold()).frame(maxWidth: .infinity, alignment: .leading)
                switch state.phase {
                case .signedOut:
                    notice("명함은 계정에 저장돼요", "로그인하면 만든 명함을 볼 수 있어요.")
                    Button("로그인") { signingIn = true }.buttonStyle(DearbyButtonStyle())
                case .noCard: newCard
                case .loading:
                    ProgressView("명함을 불러오는 중이에요").frame(maxWidth: .infinity, minHeight: 240).tint(DearbyStyle.teal)
                case .failed:
                    notice("명함을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.")
                    Button("다시 시도") { Task { await state.load() } }.buttonStyle(DearbyButtonStyle(outlined: true))
                case .loaded: cards
                }
            }.padding(20)
        }.background(.white).toolbar(.hidden, for: .navigationBar)
            .task { await state.load() }
            .refreshable { await state.load() }
            .sheet(isPresented: $editor, onDismiss: { Task { await state.load() } }) {
                NavigationStack { CardComposerPage(account: state.account) }
            }
            .sheet(item: $editingCard, onDismiss: { Task { await state.load() } }) { card in
                NavigationStack { CardComposerPage(account: state.account, prefill: card) }
            }
            .sheet(isPresented: $signingIn) {
                NavigationStack { SignInSheet(account: state.account) { Task { await state.load() } } }
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
            Button { editingCard = card } label: {
                Label("이 명함 고치기", systemImage: "pencil").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal).frame(minHeight: 44)
            }.accessibilityHint("이 명함의 이름과 공개 선택으로 새 명함을 발행해요")
            Button(role: .destructive) { deletingCard = card } label: {
                Label("이 명함 지우기", systemImage: "trash").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.danger).frame(minHeight: 44)
            }.accessibilityHint("확인 뒤 명함과 공개 링크를 지워요")
        }
    }
    private var newCard: some View {
        VStack(spacing: 24) {
            Image(systemName: "person.crop.rectangle.badge.plus").font(.system(size: 64)).foregroundStyle(DearbyStyle.teal)
            Text("첫 명함을\n만들어 보세요.").font(.title.bold()).multilineTextAlignment(.center)
            Text("프로필의 연락처와 이력 중 보여 줄 것만 골라 발행해요. 발행할 때 패스키로 로그인해요.").font(.subheadline)
                .foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
            Button("명함 만들기") { editor = true }.buttonStyle(DearbyButtonStyle())
        }.padding(24).padding(.vertical, 32).frame(maxWidth: .infinity)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
    }
    private func notice(_ title: String, _ detail: String) -> some View {
        VStack(spacing: 8) {
            Text(title).font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
            Text(detail).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
        }.frame(maxWidth: .infinity, minHeight: 160)
    }
    private func tile(selected: Bool, action: @escaping () -> Void, @ViewBuilder label: () -> some View) -> some View {
        Button(action: action) {
            label().padding(14).frame(maxWidth: .infinity, minHeight: 84, alignment: .leading)
                .background(selected ? DearbyStyle.mint : .white, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(selected ? DearbyStyle.teal : DearbyStyle.line, lineWidth: selected ? 1.5 : 1))
                .contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityAddTraits(selected ? .isSelected : [])
    }
}
