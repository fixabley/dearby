import SwiftUI

struct SendCardView: View {
    let recipient: CardModel
    let cards: [CardModel]
    let profile: ProfileModel
    var activities: [ActivityModel] = []
    let publish: (CardRequest) async throws -> Void
    let send: (String, ExchangeContextModel) async throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selection = 0
    @State private var detail: CardModel?
    private var selectedID: String? { cards.isEmpty ? nil : cards[min(selection, cards.count - 1)].id }
    @State private var activity = ExchangeActivityState()
    @State private var composing = false
    @State private var busy = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    Text("\(recipient.profileName)님에게 보낼 명함").font(.subheadline).foregroundStyle(DearbyStyle.quiet)
                        .padding(10).background(DearbyStyle.muted, in: Capsule())
                    Text("어떤 명함을 건넬까요?").font(.title2.bold())
                    Text("위아래로 밀어 명함을 골라주세요.").font(.subheadline).foregroundStyle(DearbyStyle.quiet)
                    if cards.isEmpty {
                        ContentUnavailableView("공유할 명함을 만들어 주세요", systemImage: "person.text.rectangle")
                    } else {
                        CardDeck(cards: cards, selection: $selection, showNameBadge: true).disabled(busy)
                        Button("명함 상세보기") { detail = cards[min(selection, cards.count - 1)] }
                            .buttonStyle(DearbyButtonStyle(outlined: true)).disabled(busy)
                    }
                    Text("선택한 명함의 정보만 전달돼요.").font(.caption).foregroundStyle(DearbyStyle.quiet)
                    DisclosureGroup("교환한 활동: " + ExchangeActivityState.title(activity.context, activities: activities)) {
                        ExchangeActivityPicker(activities: activities, state: $activity)
                    }.font(.caption).disabled(busy)
                    if let error { Text(error).foregroundStyle(.red) }
                    Text("상대의 받은 명함함에 직접 전달됩니다. 선택만으로 전송되지 않습니다.").font(.footnote)
                }.padding(20)
            }.background(.white).navigationTitle("내 명함 선택").navigationBarTitleDisplayMode(.inline)
                .safeAreaInset(edge: .bottom) {
                    Button(busy ? "서버 확인 중…" : "이 명함 보내기") {
                        guard let selectedID else { return }
                        busy = true
                        Task {
                            defer { busy = false }
                            do {
                                try await send(selectedID, activity.context)
                                dismiss()
                            } catch { self.error = error.localizedDescription }
                        }
                    }.buttonStyle(DearbyButtonStyle()).disabled(selectedID == nil || busy || !activity.isValid)
                        .padding(.horizontal, 20).padding(.vertical, 10).background(.white)
                }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() }.disabled(busy) }
                    ToolbarItem(placement: .primaryAction) { Button("새 명함", systemImage: "plus") { composing = true }.disabled(busy) }
                }
                .sheet(isPresented: $composing) { CardComposer(profile: profile, publish: publish) }
                .onChange(of: cards.map(\.id)) { old, new in selection = new.firstIndex { !old.contains($0) } ?? min(selection, max(0, new.count - 1)) }
                .sheet(item: $detail) { card in
                    NavigationStack {
                        ScrollView { CardView(card: card, onContact: ContactActions.perform).padding(20) }
                            .navigationTitle("명함 상세").navigationBarTitleDisplayMode(.inline)
                            .toolbar { Button("닫기") { detail = nil } }
                    }
                }
                .interactiveDismissDisabled(busy)
        }
    }
}
