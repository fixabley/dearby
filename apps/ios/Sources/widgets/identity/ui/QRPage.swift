import SwiftUI

struct QRPage: View {
    @Bindable var state: IdentityViewModel
    let account: AccountViewModel
    @State private var mode = 0
    @State private var enlarged = false
    @State private var sharing = false
    @State private var editor = false
    @State private var detail: CardModel?
    @State private var torch = false
    private var selected: CardModel? { state.cards.first { $0.id == state.selectedCardID } }
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                DearbyLogo(width: 80)
                Text("명함 교환").font(.title.bold()).frame(maxWidth: .infinity, alignment: .leading)
                DearbySegments(labels: ["QR 보여주기", "QR 찍기"], selection: $mode)
                if mode == 0 {
                    if let card = selected { display(card) } else { newCard }
                    Text("내 명함").font(.headline).frame(maxWidth: .infinity, alignment: .leading)
                    ScrollView(.horizontal) {
                        HStack(spacing: 10) {
                            tile(title: "새 명함", subtitle: "", symbol: "plus", selected: false) { editor = true }
                            ForEach(state.cards) { card in
                                tile(title: card.name, subtitle: card.description, symbol: "person", selected: card.id == state.selectedCardID) { state.selectedCardID = card.id }
                            }
                        }
                    }.scrollIndicators(.hidden)
                } else { scan }
            }.padding(20)
        }.background(.white).toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $editor) { NavigationStack { CardComposerPage(account: account) } }
            .sheet(isPresented: $sharing) { QRShareSheet() }
            .sheet(item: $detail) { card in
                NavigationStack {
                    SharedCardPage(card: card, state: state)
                        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { detail = nil } } }
                }
            }
            .fullScreenCover(isPresented: $enlarged) {
                VStack {
                    HStack { Spacer(); Button("닫기") { enlarged = false } }.padding(20)
                    Spacer()
                    Image("ExampleQR").interpolation(.none).resizable().scaledToFit().padding(28)
                    Text("예시 QR · example.com").font(.caption).foregroundStyle(DearbyStyle.quiet)
                    Spacer()
                }.background(.white)
            }
    }
    private func display(_ card: CardModel) -> some View {
        VStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(card.name).font(.title2.bold())
                    Button { editor = true } label: { Image(systemName: "ellipsis").frame(width: 44, height: 44) }.accessibilityLabel("명함 편집")
                    Spacer()
                    Button { sharing = true } label: { Image(systemName: "square.and.arrow.up").font(.title2).frame(width: 44, height: 44) }.accessibilityLabel("명함 공유")
                }
                Text(card.description).font(.subheadline).foregroundStyle(DearbyStyle.quiet)
                Button { enlarged = true } label: {
                    Image("ExampleQR").interpolation(.none).resizable().scaledToFit().frame(maxHeight: 180).frame(maxWidth: .infinity)
                }.accessibilityLabel("QR 확대")
                Label("QR을 누르면 크게 보여줘요. 예시 주소가 담겨 있어요.", systemImage: "info.circle")
                    .font(.caption).foregroundStyle(DearbyStyle.quiet)
            }.padding(16).overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
            Button("명함 보기") { detail = card }.buttonStyle(DearbyButtonStyle())
        }
    }
    private var newCard: some View {
        VStack(spacing: 24) {
            Image(systemName: "person.crop.rectangle.badge.plus").font(.system(size: 64)).foregroundStyle(DearbyStyle.teal)
            Text("이번에 공유할\n명함을 만드세요.").font(.title.bold()).multilineTextAlignment(.center)
            Text("보여줄 연락처와 활동 이력을 골라 담을 수 있어요.").font(.subheadline).foregroundStyle(DearbyStyle.quiet)
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
private struct QRShareSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var notice: String?
    var body: some View {
        VStack(spacing: 0) {
            DearbySheetHeader(title: "명함 공유") { dismiss() }
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(zip(["링크 공유", "링크 복사", "QR 이미지 저장"], ["square.and.arrow.up", "doc.on.doc", "square.and.arrow.down"])), id: \.0) { title, symbol in
                    Divider()
                    Button { notice = "\(title) 예시를 확인했어요. 외부 전달·복사·사진 저장은 하지 않아요." } label: {
                        Label(title, systemImage: symbol).font(.headline).frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 20)
                    }
                }
                Divider()
                Text(notice ?? "이름과 설명이 담긴 QR 공유 화면을 체험해요.").font(.caption).foregroundStyle(DearbyStyle.quiet).padding(.top, 16)
            }.padding(20)
            Spacer(minLength: 0)
        }.presentationDetents([.height(380), .large]).presentationDragIndicator(.visible).presentationBackground(.white)
    }
}
