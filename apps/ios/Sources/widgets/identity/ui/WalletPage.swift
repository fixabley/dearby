import SwiftUI

/// 받은 명함: the cards saved to the account, grouped by the activities they were shared with.
struct WalletPage: View {
    let state: WalletModel
    @State private var query = ""
    @State private var collapsed: Set<String> = []
    @State private var detail: CardModel?
    @State private var signingIn = false
    @Environment(\.openURL) private var openURL
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                switch state.phase {
                case .signedOut:
                    notice("받은 명함은 계정에 저장돼요", "로그인하면 저장한 명함을 볼 수 있어요. QR 탭에서 명함을 찍어 저장할 수 있어요.")
                    Button("로그인") { signingIn = true }.buttonStyle(DearbyButtonStyle())
                case .loading:
                    ProgressView("받은 명함을 불러오는 중이에요").frame(maxWidth: .infinity, minHeight: 240).tint(DearbyStyle.teal)
                case .failed:
                    notice("받은 명함을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.")
                    Button("다시 시도") { Task { await state.load() } }.buttonStyle(DearbyButtonStyle(outlined: true))
                case .loaded where state.wallet.items.isEmpty:
                    notice("아직 받은 명함이 없어요", "QR 탭에서 명함을 찍고 '받은 명함에 저장'을 눌러 보세요.")
                case .loaded:
                    DearbySearchField(prompt: "이름, 직무, 활동으로 검색", text: $query, identifier: "wallet-search")
                    let groups = state.groups(query: query, collapsed: collapsed)
                    if groups.isEmpty { notice("찾는 명함이 없어요", "이름이나 활동으로 다시 검색해 주세요.") }
                    ForEach(groups) { group in
                        DearbySectionHeader(title: group.title, count: group.items.count, expanded: group.expanded) {
                            if collapsed.contains(group.id) { collapsed.remove(group.id) } else { collapsed.insert(group.id) }
                        }
                        if group.expanded {
                            ForEach(group.items) { item in
                                ReceivedCardRow(name: item.card.profileName, job: item.card.job) { detail = item.card }
                            }
                        }
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
