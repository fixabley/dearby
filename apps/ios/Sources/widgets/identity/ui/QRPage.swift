import SwiftUI

struct QRPage: View {
    @Bindable var state: IdentityViewModel
    let share: QRShareModel
    /// Candidates to send along with the card: the activities the user marked as applied.
    let activities: [ActivityModel]
    @State private var mode = 0
    @State private var editor = false
    @State private var detail: CardModel?
    @State private var torch = false
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                DearbyLogo(width: 80)
                Text("명함 교환").font(.title.bold()).frame(maxWidth: .infinity, alignment: .leading)
                DearbySegments(labels: ["QR 보여주기", "QR 찍기"], selection: $mode)
                if mode == 0 { show } else { scan }
            }.padding(20)
        }.background(.white).toolbar(.hidden, for: .navigationBar)
            .task { await share.load() }
            .sheet(isPresented: $editor, onDismiss: { Task { await share.load() } }) {
                NavigationStack { CardComposerPage(account: share.account) }
            }
            .sheet(item: $detail) { card in
                NavigationStack {
                    SharedCardPage(card: card, state: state)
                        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { detail = nil } } }
                }
            }
    }
    @ViewBuilder private var show: some View {
        switch share.phase {
        case .signedOut, .noCard: newCard
        case .loading, .ready, .failed:
            let card = share.card
            QRShareCard(name: card?.profileName ?? "", job: card?.job ?? "", url: share.phase.url,
                        errorMessage: share.phase.error, retry: { Task { await share.load() } },
                        activities: activities.map { DearbyChoice(id: $0.id, title: $0.title) },
                        selectedActivityIDs: Binding(get: { share.activityIDs }, set: { ids in Task { await share.choose(ids) } }))
            Text("내 명함").font(.headline).frame(maxWidth: .infinity, alignment: .leading)
            ScrollView(.horizontal) {
                HStack(spacing: 10) {
                    tile(title: "새 명함", subtitle: "", symbol: "plus", selected: false) { editor = true }
                    ForEach(share.cards.reversed()) { item in
                        tile(title: item.name, subtitle: item.profileName, symbol: "person", selected: item.id == share.selectedCardID) {
                            Task { await share.select(item.id) }
                        }
                    }
                }
            }.scrollIndicators(.hidden)
        }
    }
    private var newCard: some View {
        VStack(spacing: 24) {
            Image(systemName: "person.crop.rectangle.badge.plus").font(.system(size: 64)).foregroundStyle(DearbyStyle.teal)
            Text("이번에 공유할\n명함을 만드세요.").font(.title.bold()).multilineTextAlignment(.center)
            Text("명함을 만들면 QR로 바로 건넬 수 있어요. 발행할 때 이메일로 로그인해요.").font(.subheadline)
                .foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
            Button("명함 만들기") { editor = true }.buttonStyle(DearbyButtonStyle())
        }.padding(24).padding(.vertical, 32).frame(maxWidth: .infinity)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
    }
    private var scan: some View {
        VStack(spacing: 14) {
            VStack(spacing: 20) {
                Spacer(minLength: 20)
                Image(systemName: "viewfinder").font(.system(size: 120, weight: .ultraLight))
                Text("명함의 QR 코드를 화면에 맞춰주세요.").font(.subheadline)
                Button("예시 QR 읽기") { detail = state.received[0] }.buttonStyle(DearbyButtonStyle())
                Button { torch.toggle() } label: {
                    Image(systemName: torch ? "flashlight.on.fill" : "flashlight.off.fill").font(.title2)
                        .padding(16).background(.white.opacity(torch ? 0.35 : 0.15), in: Circle())
                }.accessibilityLabel("예시 손전등")
                Spacer(minLength: 0)
            }.padding(20).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 370)
                .background(Color(white: 0.2), in: RoundedRectangle(cornerRadius: 12))
            Button("사진에서 선택", systemImage: "photo") { detail = state.received[0] }.buttonStyle(DearbyButtonStyle(outlined: true))
            Text("카메라·사진에 접근하지 않고 예시 명함을 보여줘요.").font(.caption).foregroundStyle(DearbyStyle.quiet)
        }
    }
    private func tile(title: String, subtitle: String, symbol: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Image(systemName: symbol).font(.title2)
                Text(title).font(.subheadline.bold())
                if !subtitle.isEmpty { Text(subtitle).font(.caption2).lineLimit(2) }
            }.frame(width: 116, height: 100).padding(8)
                .foregroundStyle(selected ? DearbyStyle.teal : DearbyStyle.quiet)
                .background(selected ? DearbyStyle.mint : .white, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? DearbyStyle.teal : DearbyStyle.line, lineWidth: selected ? 1.5 : 1))
        }.buttonStyle(.plain).accessibilityAddTraits(selected ? .isSelected : [])
    }
}
private extension QRShareModel.Phase {
    var url: URL? { if case .ready(let url) = self { url } else { nil } }
    var error: String? { if case .failed(let text) = self { text } else { nil } }
}
