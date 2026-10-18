import SwiftUI

/// 내 명함: the account's published cards. Pick one to view it, make a new one, or republish it with "이 명함 고치기".
struct MyCardsPage: View {
    let state: MyCardsModel
    @State private var editor = false
    /// 명함 고치기: a new publication prefilled from this card's template (cards never change after publishing).
    @State private var editingCard: PublishedCard?
    @State private var signingIn = false
    @Environment(\.openURL) private var openURL
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
    }
    @ViewBuilder private var cards: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 10) {
                tile(title: "새 명함", subtitle: "", symbol: "plus", selected: false) { editor = true }
                ForEach(state.cards.reversed()) { item in
                    tile(title: item.name, subtitle: item.profileName, symbol: "person", selected: item.id == state.selectedCardID) {
                        state.select(item.id)
                    }
                }
            }
        }.scrollIndicators(.hidden)
        if let card = state.card {
            CardView(card: card.cardModel) { if let url = $0.actionURL { openURL(url) } }
                .padding(20).background(.white, in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(DearbyStyle.line))
            Button { editingCard = card } label: {
                Label("이 명함 고치기", systemImage: "pencil").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal).frame(minHeight: 44)
            }.accessibilityHint("이 명함의 이름과 공개 선택으로 새 명함을 발행해요")
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
    private func tile(title: String, subtitle: String, symbol: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Image(systemName: symbol).font(.title2)
                Text(title).font(.subheadline.bold())
                if !subtitle.isEmpty { Text(subtitle).font(.caption2).lineLimit(2) }
            }.frame(width: 116, height: 100).padding(8)
                .foregroundStyle(selected ? DearbyStyle.teal : DearbyStyle.quiet)
                .background(selected ? DearbyStyle.mint : .white, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? DearbyStyle.teal : DearbyStyle.line, lineWidth: selected ? 1.5 : 1))
        }.buttonStyle(.plain).accessibilityAddTraits(selected ? .isSelected : [])
    }
}
