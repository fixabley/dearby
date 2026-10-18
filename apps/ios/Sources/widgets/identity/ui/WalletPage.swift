import SwiftUI

/// 받은 명함: the cards saved to the account as an indexed list (contract "받은 명함 목록 표시"):
/// first-letter sections with pinned headers, a right-edge index rail, and the existing search field.
struct WalletPage: View {
    let state: WalletModel
    @State private var query = ""
    @State private var detail: CardModel?
    @State private var signingIn = false
    @Environment(\.openURL) private var openURL
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The rail stays hidden for short lists.
    private static let railThreshold = 6
    var body: some View {
        ScrollViewReader { proxy in
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
                        let sections = state.sections(query: query)
                        if sections.isEmpty { notice("찾는 명함이 없어요", "이름이나 직무로 다시 검색해 주세요.") }
                        list(sections)
                    }
                }.padding(.horizontal, 20).padding(.vertical, 12)
                    // Leave room for the rail so rows never sit under it.
                    .padding(.trailing, showsRail ? 20 : 0)
            }
            .overlay(alignment: .trailing) {
                if showsRail { IndexRail(keys: state.sections(query: query).map(\.key)) { jump(to: $0, proxy) } }
            }
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
    private var showsRail: Bool { state.phase == .loaded && state.sections(query: query).count > Self.railThreshold }
    private func list(_ sections: [(key: String, entries: [WalletModel.Entry])]) -> some View {
        LazyVStack(alignment: .leading, spacing: 0, pinnedViews: .sectionHeaders) {
            ForEach(sections, id: \.key) { section in
                Section {
                    ForEach(section.entries) { entry in
                        ReceivedCardRow(name: entry.card.profileName, job: entry.card.job, organization: entry.organization) { detail = entry.card }
                        Divider()
                    }
                } header: {
                    Text(section.key).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 8).padding(.vertical, 4)
                        .background(DearbyStyle.muted).accessibilityAddTraits(.isHeader).id(section.key)
                }
            }
        }
    }
    private func jump(to key: String, _ proxy: ScrollViewProxy) {
        if reduceMotion { proxy.scrollTo(key, anchor: .top) } else { withAnimation(.snappy) { proxy.scrollTo(key, anchor: .top) } }
    }
    private func notice(_ title: String, _ detail: String) -> some View {
        VStack(spacing: 8) {
            Text(title).font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
            Text(detail).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
        }.frame(maxWidth: .infinity, minHeight: 160)
    }
}

/// Right-edge index: one letter per non-empty section. Tap or drag along it to jump; each letter is a "<key>으로 이동" button.
private struct IndexRail: View {
    let keys: [String]
    let jump: (String) -> Void
    @State private var dragging: String?
    var body: some View {
        GeometryReader { geometry in
            let height = geometry.size.height / CGFloat(max(keys.count, 1))
            VStack(spacing: 0) {
                ForEach(keys, id: \.self) { key in
                    Button { jump(key) } label: {
                        Text(key).font(.dearby(fixedSize: 11, weight: .semibold)).foregroundStyle(dragging == key ? .white : DearbyStyle.teal)
                            .frame(width: 20, height: height)
                            .background(dragging == key ? DearbyStyle.teal : .clear, in: Capsule())
                    }.buttonStyle(.plain).accessibilityLabel("\(key)으로 이동")
                }
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let index = min(max(Int(value.location.y / height), 0), keys.count - 1)
                        let key = keys[index]
                        if key != dragging { dragging = key; jump(key) }
                    }
                    .onEnded { _ in dragging = nil }
            )
        }
        .frame(width: 24)
        .padding(.trailing, 4)
        .padding(.vertical, 60)
        .accessibilityElement(children: .contain).accessibilityLabel("색인")
    }
}
