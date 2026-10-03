import SwiftUI

struct WalletPage: View {
    let state: IdentityViewModel
    @State private var query = ""
    @State private var group = 0
    @State private var selection = 0
    @State private var detail: CardModel?
    @State private var recipient: CardModel?
    private var pending: [CardModel] { state.received.filter { !state.reciprocalIDs.contains($0.id) } }
    private var mutual: [CardModel] { state.received.filter { state.reciprocalIDs.contains($0.id) } }
    private var cards: [CardModel] {
        (pending.isEmpty || group == 1 ? mutual : pending).filter {
            query.isEmpty || ($0.profileName + $0.job + $0.histories.map(\.title).joined()).localizedCaseInsensitiveContains(query)
        }
    }
    private var selected: CardModel? { cards.indices.contains(selection) ? cards[selection] : cards.first }
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                HStack {
                    Image(systemName: "magnifyingglass")
                    TextField("이름, 직무, 활동으로 검색", text: $query).accessibilityIdentifier("wallet-search")
                }.padding(12).background(DearbyStyle.muted, in: Capsule())
                if !pending.isEmpty {
                    HStack {
                        groupButton("내 명함을 주지 않은 상대 \(pending.count)", index: 0)
                        groupButton("서로 주고받은 상대 \(mutual.count)", index: 1)
                    }
                    Text(group == 0 ? "아직 내 명함을 건네지 않았어요." : "서로 명함을 주고받았어요.")
                        .font(.caption).foregroundStyle(DearbyStyle.quiet)
                }
                if cards.isEmpty {
                    ContentUnavailableView("찾는 명함이 없어요", systemImage: "person.text.rectangle", description: Text("이름이나 활동으로 다시 검색해 주세요."))
                } else {
                    CardDeck(cards: cards, selection: $selection)
                    Text("위아래로 밀어 명함을 넘겨요.").font(.caption).foregroundStyle(DearbyStyle.quiet)
                    HStack(spacing: 10) {
                        Button("명함 상세보기") { detail = selected }.buttonStyle(DearbyButtonStyle(outlined: true))
                        if let selected, !state.reciprocalIDs.contains(selected.id) {
                            Button("나도 명함 주기") { recipient = selected }.buttonStyle(DearbyButtonStyle())
                        }
                    }
                }
            }.padding(.horizontal, 20).padding(.vertical, 12)
        }.background(.white).navigationTitle("받은 명함").navigationBarTitleDisplayMode(.inline)
            .onChange(of: query) { _, _ in selection = 0 }
            .sheet(item: $detail) { card in
                NavigationStack {
                    SharedCardPage(card: card, state: state)
                        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { detail = nil } } }
                }
            }
            .sheet(item: $recipient) { card in NavigationStack { SendCardPage(state: state, recipient: card) } }
    }
    private func groupButton(_ title: String, index: Int) -> some View {
        Button { group = index; selection = 0 } label: {
            Text(title).font(.caption.weight(group == index ? .bold : .regular)).frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(group == index ? DearbyStyle.teal : DearbyStyle.quiet)
                .overlay(alignment: .bottom) { Rectangle().fill(group == index ? DearbyStyle.teal : DearbyStyle.line).frame(height: group == index ? 3 : 1) }
        }.accessibilityIdentifier("wallet-group-\(index)")
    }
}
