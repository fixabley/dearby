import SwiftUI

/// 받은 명함: the cards saved to the account, as one searchable list.
struct WalletPage: View {
    let state: WalletModel
    @State private var query = ""
    @State private var detail: CardModel?
    @State private var signingIn = false
    @Environment(\.openURL) private var openURL
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                switch state.phase {
                case .signedOut:
                    notice("받은 명함은 계정에 저장돼요", "로그인하면 저장한 명함을 볼 수 있어요.")
                    Button("로그인") { signingIn = true }.buttonStyle(DearbyButtonStyle())
                case .loading:
                    ProgressView("받은 명함을 불러오는 중이에요").frame(maxWidth: .infinity, minHeight: 240).tint(DearbyStyle.teal)
                case .failed:
                    notice("받은 명함을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.")
                    Button("다시 시도") { Task { await state.load() } }.buttonStyle(DearbyButtonStyle(outlined: true))
                case .loaded where state.wallet.items.isEmpty:
                    notice("아직 받은 명함이 없어요", "계정에 저장된 명함이 여기에 보여요.")
                case .loaded:
                    DearbySearchField(prompt: "이름, 직무, 이력으로 검색", text: $query, identifier: "wallet-search")
                    let cards = state.cards(query: query)
                    if cards.isEmpty { notice("찾는 명함이 없어요", "이름이나 직무로 다시 검색해 주세요.") }
                    ForEach(cards) { card in
                        ReceivedCardRow(name: card.profileName, job: card.job) { detail = card }
                    }
                }
            }.padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(.white).navigationTitle("받은 명함").navigationBarTitleDisplayMode(.inline)
        .task { await state.load() }
        .refreshable { await state.load() }
        .sheet(item: $detail) { card in
            NavigationStack {
                ScrollView { CardView(card: card) { if let url = $0.actionURL { openURL(url) } }.padding(20) }
                    .navigationTitle(card.profileName).navigationBarTitleDisplayMode(.inline)
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { detail = nil } } }
            }
        }
        .sheet(isPresented: $signingIn) {
            NavigationStack { SignInSheet(account: state.account) { Task { await state.load() } } }
        }
    }
    private func notice(_ title: String, _ detail: String) -> some View {
        VStack(spacing: 8) {
            Text(title).font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
            Text(detail).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
        }.frame(maxWidth: .infinity, minHeight: 160)
    }
}
