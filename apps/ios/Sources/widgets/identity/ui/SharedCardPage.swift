import SwiftUI

struct SharedCardPage: View {
    let card: CardModel
    let state: IdentityViewModel
    @State private var sending = false
    @State private var notice: String?
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                CardView(card: card) { contact in notice = "\(contact.displayLabel) · \(contact.value)\n예시 연락처이며 연락을 실행하지 않아요." }
                Divider()
                Text("이번 실행 동안 명함함에 보관할 수 있어요.").font(.caption).foregroundStyle(DearbyStyle.quiet)
                Button(state.savedCardIDs.contains(card.id) ? "카드 저장됨" : "카드 저장") { state.save(card) }
                    .buttonStyle(DearbyButtonStyle()).accessibilityIdentifier("save-shared-card")
                Button(state.reciprocalIDs.contains(card.id) ? "명함을 주고받았어요" : "나도 카드 주기") { sending = true }
                    .buttonStyle(DearbyButtonStyle(outlined: true))
            }.padding(20)
        }.background(.white).navigationTitle("공유 카드").navigationBarTitleDisplayMode(.inline)
            .toolbar { DearbyLogo(width: 72) }
            .sheet(isPresented: $sending) { NavigationStack { SendCardPage(state: state, recipient: card) } }
            .alert("예시 연락처", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) {
                Button("확인") { notice = nil }
            } message: { Text(notice ?? "") }
    }
}
struct SendCardPage: View {
    let state: IdentityViewModel
    let recipient: CardModel
    @State private var selection = 0
    @State private var editor = false
    @State private var preview = false
    @State private var sent = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                DearbyBadge(title: "\(recipient.profileName)님에게 보낼 명함")
                Text("어떤 명함을 건넬까요?").font(.title2.bold())
                Text("위아래로 밀어 명함을 골라주세요.").font(.subheadline).foregroundStyle(DearbyStyle.quiet)
                CardDeck(cards: state.cards, selection: $selection, showNameBadge: true)
                Button("명함 상세보기") { preview = true }.buttonStyle(DearbyButtonStyle(outlined: true))
                    .accessibilityIdentifier("send-card-preview")
            }.padding(.horizontal, 20).padding(.vertical, 12)
        }.safeAreaInset(edge: .bottom) {
            VStack(spacing: 8) {
                Text("선택한 정보로 보내기를 체험해요. 실제 전달은 하지 않아요.").font(.caption).foregroundStyle(DearbyStyle.quiet)
                Button("이 명함 보내기") { state.send(to: recipient); sent = true }.buttonStyle(DearbyButtonStyle())
                    .disabled(state.cards.isEmpty)
            }.padding(20).background(.white)
        }.navigationTitle("내 명함 선택").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } }
                ToolbarItem(placement: .primaryAction) { Button("+ 새 명함") { editor = true } }
            }
            .sheet(isPresented: $editor) { NavigationStack { CardEditorPage(state: state) } }
            .sheet(isPresented: $preview) {
                NavigationStack {
                    if state.cards.indices.contains(selection) {
                        SharedCardPage(card: state.cards[selection], state: state)
                            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { preview = false } } }
                    }
                }
            }
            .alert("명함 보내기 완료", isPresented: $sent) { Button("확인") { dismiss() } } message: {
                Text("예시 화면에만 반영했어요. 상대에게 전송되지 않았습니다.")
            }
    }
}
