import SwiftUI

struct WalletPage: View {
    let receipts: [ReceiptModel]
    let guests: [GuestSavedCardModel]
    let importAction: () -> Void
    let send: (ReceiptModel) -> Void
    let refresh: () async -> Void
    @State private var search = ""
    @State private var group = false
    private var hasUnreturned: Bool { receipts.contains { !$0.reciprocal } }
    private var shown: [ReceiptModel] {
        receipts.filter { receipt in
            (!hasUnreturned || receipt.reciprocal == group) && (search.isEmpty ||
                [receipt.card.profileName, receipt.card.job, receipt.context.label ?? ""].joined(separator: " ")
                    .localizedCaseInsensitiveContains(search))
        }
    }
    var body: some View {
        VStack {
            if !guests.isEmpty {
                Button("기기에 저장한 명함 \(guests.count)개 가져오기", action: importAction)
                    .padding(.horizontal)
            }
            if hasUnreturned {
                Picker("교환 상태", selection: $group) {
                    Text("내 명함을 주지 않은 상대").tag(false)
                    Text("서로 주고받은 상대").tag(true)
                }.pickerStyle(.segmented).padding(.horizontal)
            }
            if shown.isEmpty {
                ContentUnavailableView("받은 명함이 없어요", systemImage: "rectangle.stack",
                    description: Text("기기 저장과 계정의 받은 명함은 구분해 보관합니다."))
            } else {
                ScrollView {
                    LazyVStack(spacing: 20) {
                        ForEach(shown) { receipt in
                            VStack(alignment: .leading, spacing: 16) {
                                CardView(card: receipt.card, onContact: ContactActions.perform)
                                Text((receipt.context.label ?? "활동 선택 안 함") + " · " + receipt.receivedAt)
                                    .font(.caption).foregroundStyle(.secondary)
                                Button("나도 명함 주기") { send(receipt) }.buttonStyle(.borderedProminent)
                            }.containerRelativeFrame(.vertical, alignment: .top)
                        }
                    }.scrollTargetLayout().padding()
                }.scrollTargetBehavior(.viewAligned)
                    .simultaneousGesture(DragGesture(minimumDistance: 50).onEnded { value in
                        if hasUnreturned && abs(value.translation.width) > abs(value.translation.height) {
                            group = value.translation.width < 0
                        }
                    })
            }
        }
        .navigationTitle("받은 명함").searchable(text: $search, prompt: "이름 · 직무 · 활동 검색")
        .refreshable { await refresh() }
    }
}

struct GuestImportView: View {
    let guests: [GuestSavedCardModel]
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
                                Text(guest.context.label ?? "활동 선택 안 함").font(.caption)
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
