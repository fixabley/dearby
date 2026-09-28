import SwiftUI

struct WalletPage: View {
    let receipts: [ReceiptModel]
    let guests: [GuestSavedCardModel]
    var activities: [ActivityModel] = []
    let importAction: () -> Void
    let send: (ReceiptModel) -> Void
    let refresh: () async -> Void
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var search = ""
    @State private var group = false
    @State private var selection = 0
    @State private var detail: CardModel?
    private var hasUnreturned: Bool { receipts.contains { !$0.reciprocal } }
    private var shown: [ReceiptModel] {
        receipts.filter { receipt in
            (!hasUnreturned || receipt.reciprocal == group) && (search.isEmpty ||
                [receipt.card.profileName, receipt.card.job, ExchangeActivityState.title(receipt.context, activities: activities)].joined(separator: " ")
                    .localizedCaseInsensitiveContains(search))
        }
    }
    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass").foregroundStyle(DearbyStyle.quiet)
                    TextField("이름, 직무, 활동으로 검색", text: $search)
                }.padding(13).background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 12))
                if !guests.isEmpty { Button("기기에 저장한 명함 \(guests.count)개 가져오기", action: importAction) }
                if hasUnreturned {
                    HStack(spacing: 0) {
                        groupButton("내 명함을 주지 않은 상대", reciprocal: false)
                        groupButton("서로 주고받은 상대", reciprocal: true)
                    }
                    Text(group ? "서로 명함을 주고받았어요." : "아직 내 명함을 건네지 않았어요.")
                        .font(.caption).foregroundStyle(DearbyStyle.quiet)
                }
                if shown.isEmpty {
                    ContentUnavailableView("받은 명함이 없어요", systemImage: "rectangle.stack",
                        description: Text("기기 저장과 계정의 받은 명함은 구분해 보관합니다."))
                } else {
                    CardDeck(cards: shown.map(\.card), selection: $selection)
                    Text("위아래로 밀어 명함을 넘겨요.").font(.caption).foregroundStyle(DearbyStyle.quiet)
                    let receipt = shown[min(selection, shown.count - 1)]
                    Text(ExchangeActivityState.title(receipt.context, activities: activities) + " · " + receipt.receivedAt)
                        .font(.caption2).foregroundStyle(DearbyStyle.quiet)
                    if dynamicTypeSize.isAccessibilitySize { actions(receipt) }
                }
            }.padding(20)
        }.background(.white)
            .navigationTitle("받은 명함").navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if !shown.isEmpty && !dynamicTypeSize.isAccessibilitySize {
                    actions(shown[min(selection, shown.count - 1)])
                        .padding(.horizontal, 20).padding(.vertical, 10).background(.white)
                }
            }
            .refreshable { await refresh() }
            .onChange(of: search) { _, _ in selection = 0 }
            .onChange(of: group) { _, _ in selection = 0 }
            .simultaneousGesture(DragGesture(minimumDistance: 50).onEnded { value in
                if hasUnreturned && abs(value.translation.width) > abs(value.translation.height) { group = value.translation.width < 0 }
            })
            .sheet(item: $detail) { card in
                NavigationStack {
                    ScrollView { CardView(card: card, onContact: ContactActions.perform).padding(20) }
                        .navigationTitle("공유 카드").navigationBarTitleDisplayMode(.inline)
                        .toolbar { Button("닫기") { detail = nil } }
                }
            }
    }
    private func actions(_ receipt: ReceiptModel) -> some View {
        let layout = dynamicTypeSize.isAccessibilitySize ? AnyLayout(VStackLayout(spacing: 10)) : AnyLayout(HStackLayout(spacing: 10))
        return layout {
            Button("명함 상세보기") { detail = receipt.card }.buttonStyle(DearbyButtonStyle(outlined: true))
            Button("나도 명함 주기") { send(receipt) }.buttonStyle(DearbyButtonStyle())
        }
    }
    private func groupButton(_ title: String, reciprocal: Bool) -> some View {
        Button { group = reciprocal } label: {
            VStack(spacing: 10) {
                Text(title + " " + String(Set(receipts.filter { $0.reciprocal == reciprocal }.map { $0.card.ownerId }).count))
                    .font(.caption.weight(group == reciprocal ? .bold : .regular)).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, minHeight: 44)
                Rectangle().fill(group == reciprocal ? DearbyStyle.teal : DearbyStyle.line).frame(height: 2)
            }.foregroundStyle(group == reciprocal ? DearbyStyle.teal : DearbyStyle.quiet)
        }.buttonStyle(.plain).accessibilityAddTraits(group == reciprocal ? .isSelected : [])
    }
}

struct GuestImportView: View {
    let guests: [GuestSavedCardModel]
    var activities: [ActivityModel] = []
    let resolve: (String) async throws -> CardModel
    let importAction: (Set<String>) async throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selected: Set<String> = []
    @State private var cache: [String: CardModel] = [:]
    @State private var busy = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            List {
                Text("Dearby에 기기 저장한 명함입니다. 휴대폰 주소록은 읽지 않습니다.").font(.footnote)
                HStack {
                    Button("전체 선택") { selected = Set(guests.map(\.cardId)) }
                    Spacer()
                    Button("선택 해제") { selected.removeAll() }
                }
                ForEach(guests) { guest in
                    Button {
                        if selected.contains(guest.cardId) { selected.remove(guest.cardId) } else { selected.insert(guest.cardId) }
                    } label: {
                        HStack {
                            Image(systemName: selected.contains(guest.cardId) ? "checkmark.square.fill" : "square")
                            VStack(alignment: .leading) {
                                Text(cache[guest.cardId]?.profileName ?? "명함 정보 미확인")
                                Text(cache[guest.cardId]?.job ?? guest.cardId).font(.caption)
                                Text(ExchangeActivityState.title(guest.context, activities: activities)).font(.caption)
                            }
                        }
                    }.accessibilityAddTraits(selected.contains(guest.cardId) ? .isSelected : [])
                }
                if let error { Text(error).foregroundStyle(.red) }
                Button("선택한 명함 \(selected.count)개 가져오기") {
                    busy = true
                    Task {
                        defer { busy = false }
                        do { try await importAction(selected); selected.removeAll() } catch { self.error = error.localizedDescription }
                    }
                }.disabled(busy || selected.isEmpty)
            }.navigationTitle("기기 명함 가져오기")
                .toolbar { Button("나중에") { dismiss() }.disabled(busy) }
                .interactiveDismissDisabled(busy)
                .task {
                    for guest in guests {
                        do { cache[guest.cardId] = try await resolve(guest.cardId) } catch { self.error = "일부 명함 정보를 불러오지 못했습니다. 기기 저장은 유지됩니다." }
                    }
                }
        }
    }
}
