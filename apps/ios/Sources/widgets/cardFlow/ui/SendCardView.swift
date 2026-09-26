import SwiftUI

struct SendCardView: View {
    let recipient: CardModel
    let cards: [CardModel]
    let profile: ProfileModel
    let publish: (CardRequest) async throws -> Void
    let send: (String, ExchangeContextModel) async throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selectedID: String?
    @State private var activity = ""
    @State private var composing = false
    @State private var busy = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    Text("\(recipient.profileName)님께 보낼 명함을 선택하세요.")
                    ForEach(cards) { card in
                        VStack {
                            CardView(card: card, onContact: ContactActions.perform)
                            Button(selectedID == card.id ? "선택됨" : "이 명함 선택") { selectedID = card.id }
                                .buttonStyle(.bordered)
                        }
                    }
                    TextField("교환한 활동 (선택)", text: $activity).textFieldStyle(.roundedBorder)
                    if let error { Text(error).foregroundStyle(.red) }
                    Button(busy ? "서버 확인 중…" : "이 명함 보내기") {
                        guard let selectedID else { return }
                        busy = true
                        Task {
                            defer { busy = false }
                            do {
                                try await send(selectedID, ExchangeContextModel(label: activity.isEmpty ? nil : activity))
                                dismiss()
                            } catch { self.error = error.localizedDescription }
                        }
                    }.buttonStyle(.borderedProminent).disabled(selectedID == nil || busy)
                    Text("상대의 받은 명함함에 직접 전달됩니다. 선택만으로 전송되지 않습니다.").font(.footnote)
                }.padding()
            }.navigationTitle("내 명함 선택")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() }.disabled(busy) }
                    ToolbarItem(placement: .primaryAction) { Button("새 명함", systemImage: "plus") { composing = true } }
                }
                .sheet(isPresented: $composing) { CardComposer(profile: profile, publish: publish) }
                .onChange(of: cards.map(\.id)) { old, new in selectedID = new.first { !old.contains($0) } ?? selectedID }
                .interactiveDismissDisabled(busy)
        }
    }
}
