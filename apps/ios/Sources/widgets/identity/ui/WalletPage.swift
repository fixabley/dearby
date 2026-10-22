import SwiftUI

/// 받은 명함: the cards saved to the account as an indexed list (contract "받은 명함 목록 표시"):
/// first-letter sections with pinned headers, a right-edge index rail, and the existing search field.
/// Rows carry the program chip and recall badge, the detail offers `내 명함도 건네기`, and a card arriving by
/// `dearby://cards/…` is saved and opened here (contract "명함 교환" 2026-10-19). Shown as the 받은 명함 section of the 명함 tab (`CardsPage`,
/// contract "명함 탭 통합"), which owns the navigation bar.
struct WalletPage: View {
    let state: WalletModel
    @State private var query = ""
    @State private var signingIn = false
    /// `내 명함도 건네기` with several cards: pick one.
    @State private var choosingCard: [PublishedCard]?
    @State private var reciprocating = false
    /// 신고하기 sheet and 삭제하고 차단하기 confirmation for the open card, and the detail's own notice (an alert under the sheet would not show).
    @State private var reporting: WalletModel.Entry?
    @State private var blocking: WalletModel.Entry?
    @State private var detailNotice: String?
    @Environment(\.openURL) private var openURL
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The rail stays hidden for short lists.
    private static let railThreshold = 6
    var body: some View {
        VStack(spacing: 0) {
            // The search field stays put; only the list scrolls (contract "비스크롤 화면 배치").
            switch state.phase {
            case .signedOut:
                CenteredMessage(symbol: "person.text.rectangle", title: "받은 명함은 계정에 저장돼요", message: "로그인하면 저장한 명함을 볼 수 있어요.",
                                primary: MessageAction("로그인") { signingIn = true })
            case .loading:
                ProgressView("받은 명함을 불러오는 중이에요").frame(maxWidth: .infinity, maxHeight: .infinity).tint(DearbyStyle.teal)
            case .failed:
                CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "받은 명함을 불러오지 못했어요", message: "연결을 확인하고 다시 시도해 주세요.",
                                primary: MessageAction("다시 시도") { Task { await state.load() } })
            case .loaded where state.wallet.items.isEmpty:
                CenteredMessage(symbol: "person.text.rectangle", title: "아직 받은 명함이 없어요", message: "상대의 명함 QR을 카메라로 읽어 저장하면 여기에 보여요.")
            case .loaded:
                DearbySearchField(prompt: "이름, 직무, 이력으로 검색", text: $query, identifier: "wallet-search")
                    .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 10)
                let sections = state.sections(query: query)
                if sections.isEmpty {
                    CenteredMessage(symbol: "magnifyingglass", title: "찾는 명함이 없어요", message: "이름이나 직무로 다시 검색해 주세요.")
                } else {
                    ScrollViewReader { proxy in
                        ScrollView {
                            list(sections).padding(.horizontal, 20).padding(.bottom, 20)
                                // Leave room for the rail so rows never sit under it.
                                .padding(.trailing, showsRail ? 20 : 0)
                        }
                        .overlay(alignment: .trailing) {
                            if showsRail { IndexRail(keys: sections.map(\.key)) { jump(to: $0, proxy) } }
                        }
                        .refreshable { await state.load() }
                    }
                }
            }
        }
        .task { await state.load() }
        // A QR-received card while signed out: sign in first, then the save continues.
        .onChange(of: state.pending, initial: true) { _, pending in if pending != nil { signingIn = true } }
        .sheet(item: Binding(get: { state.opened }, set: { state.opened = $0 })) { entry in NavigationStack { detail(entry) } }
        .sheet(isPresented: $signingIn, onDismiss: { state.dropPending() }) {
            NavigationStack { SignInSheet(account: state.account) { Task { await state.resumePending(); await state.load() } } }
        }
        .alert(outcomeTitle, isPresented: Binding(get: { state.outcome != nil }, set: { if !$0 { state.clearOutcome() } })) {
            Button("확인") {}
        } message: { Text(outcomeMessage) }
    }
    private var showsRail: Bool { state.phase == .loaded && state.sections(query: query).count > Self.railThreshold }
    /// The indexed rows on one secondary grouped surface (the page background is grouped grey, so a bare list would not read as a list).
    private func list(_ sections: [(key: String, entries: [WalletModel.Entry])]) -> some View {
        LazyVStack(alignment: .leading, spacing: 0, pinnedViews: .sectionHeaders) {
            ForEach(sections, id: \.key) { section in
                Section {
                    ForEach(section.entries) { entry in
                        ReceivedCardRow(name: entry.card.profileName, job: entry.card.job, organization: entry.organization,
                                        program: entry.program, badge: entry.badge) { state.opened = entry }
                        if entry.id != section.entries.last?.id { Divider().padding(.horizontal, 12) }
                    }
                } header: {
                    Text(section.key).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 12).padding(.vertical, 4)
                        .background(DearbyStyle.muted).accessibilityAddTraits(.isHeader).id(section.key)
                }
            }
        }
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
        .clipShape(.rect(cornerRadius: 20, style: .continuous))
    }
    /// The received card: where and when it was exchanged, the card, then `내 명함도 건네기` or the `맞교환했어요` badge.
    private func detail(_ entry: WalletModel.Entry) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(entry.receivedLine).font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
                CardView(card: entry.card) { if let url = $0.actionURL { openURL(url) } }
                if entry.receipt.reciprocal {
                    DearbyBadge(title: "맞교환했어요")
                } else {
                    Button { Task { await offerMyCard(entry) } } label: {
                        Label("내 명함도 건네기", systemImage: "person.text.rectangle").font(.dearby(.subheadline).weight(.semibold))
                            .foregroundStyle(DearbyStyle.teal).frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                    }.disabled(reciprocating).accessibilityHint("상대의 받은 명함에 내 명함을 넣어요. 맞교환한 명함은 회수되지 않아요")
                    if !entry.badge.isEmpty {
                        Text("맞교환하지 않으면 \(entry.badge). 맞교환한 명함은 서로 남아요.").font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
                    }
                }
            }.padding(20)
        }
        .navigationTitle(entry.card.profileName).navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("닫기") { state.opened = nil } }
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button { reporting = entry } label: { Label("신고하기", systemImage: "exclamationmark.bubble") }
                    Button(role: .destructive) { blocking = entry } label: { Label("삭제하고 차단하기", systemImage: "hand.raised") }
                } label: { Image(systemName: "ellipsis.circle") }.accessibilityLabel("더 보기")
            }
        }
        .sheet(item: $reporting) { entry in
            NavigationStack {
                CardReportSheet(name: entry.card.profileName) { reason, detail in
                    let sent = await state.report(entry, reason: reason, detail: detail)
                    detailNotice = sent ? "신고를 받았어요. 24시간 안에 확인할게요." : "신고를 보내지 못했어요. 연결을 확인하고 다시 시도해 주세요."
                    return sent
                }
            }
        }
        .confirmationDialog("삭제하고 차단할까요?", isPresented: Binding(get: { blocking != nil }, set: { if !$0 { blocking = nil } }),
                            titleVisibility: .visible, presenting: blocking) { entry in
            Button("삭제하고 차단하기", role: .destructive) {
                Task { if !(await state.block(entry)) { detailNotice = "차단하지 못했어요. 연결을 확인하고 다시 시도해 주세요." } }
            }
            Button("취소", role: .cancel) {}
        } message: { entry in Text("\(entry.card.profileName)님의 명함을 지우고, 앞으로 이 사람의 명함을 받지 않아요.") }
        .alert(detailNotice ?? "", isPresented: Binding(get: { detailNotice != nil }, set: { if !$0 { detailNotice = nil } })) {
            Button("확인", role: .cancel) {}
        }
        .confirmationDialog("어떤 명함을 건널까요?", isPresented: Binding(get: { choosingCard != nil }, set: { if !$0 { choosingCard = nil } }),
                            titleVisibility: .visible, presenting: choosingCard) { cards in
            ForEach(cards) { card in Button(card.name) { Task { await reciprocate(entry, card) } } }
            Button("취소", role: .cancel) {}
        }
    }
    /// One card goes straight out; several ask which; none means the composer has to come first.
    private func offerMyCard(_ entry: WalletModel.Entry) async {
        guard let cards = await state.myCards() else { return }
        switch cards.count {
        case 0: choosingCard = nil
        case 1: await reciprocate(entry, cards[0])
        default: choosingCard = cards.reversed()
        }
    }
    private func reciprocate(_ entry: WalletModel.Entry, _ card: PublishedCard) async {
        reciprocating = true
        await state.reciprocate(entry, myCardID: card.id)
        reciprocating = false
    }
    private var outcomeTitle: String {
        switch state.outcome {
        case .ownCard: "내 명함이에요"
        case .codeExpired: "QR이 만료됐어요"
        case .blocked: "차단한 사람의 명함이에요"
        case .saveFailed: "명함을 저장하지 못했어요"
        case .reciprocateFailed: "명함을 건네지 못했어요"
        case nil: ""
        }
    }
    private var outcomeMessage: String {
        switch state.outcome {
        case .ownCard: "내가 발행한 명함은 받은 명함에 넣을 수 없어요."
        case .codeExpired: "명함 주인에게 QR을 다시 보여 달라고 하세요"
        case .blocked: "설정의 차단한 사람에서 해제하면 다시 받을 수 있어요."
        case .saveFailed: "명함이 없거나 연결이 끊겼어요. QR을 다시 읽어 주세요."
        case .reciprocateFailed: "발행한 명함이 없으면 내 명함 탭에서 먼저 만들어 주세요. 연결도 확인해 주세요."
        case nil: ""
        }
    }
    private func jump(to key: String, _ proxy: ScrollViewProxy) {
        if reduceMotion { proxy.scrollTo(key, anchor: .top) } else { withAnimation(.snappy) { proxy.scrollTo(key, anchor: .top) } }
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
                        Text(key).font(.dearby(fixedSize: 11, weight: .semibold)).foregroundStyle(dragging == key ? DearbyStyle.onTeal : DearbyStyle.teal)
                            .frame(width: 20, height: height)
                            .background(dragging == key ? DearbyStyle.tealFill : .clear, in: Capsule())
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
