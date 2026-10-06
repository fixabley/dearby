import PhotosUI
import SwiftUI

struct QRPage: View {
    let share: QRShareModel
    /// Candidates to send along with the card: the activities the user marked as applied.
    let activities: [ActivityModel]
    /// Turns scanned text into a share or legacy card link for this build's web origin.
    let parse: (String) -> ScannedLink?
    /// A `/s/<UUID>` universal link the app was opened with.
    @Binding var opened: URL?
    @State private var mode = 0
    @State private var editor = false
    @State private var camera: QRCameraView.Access?
    @State private var scanRound = 0
    @State private var photo: PhotosPickerItem?
    @State private var scanError: String?
    @State private var received: ScannedLink?
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
            .onChange(of: opened, initial: true) { _, url in
                guard let url else { return }
                opened = nil
                scanned(url.absoluteString)
            }
            .sheet(isPresented: $editor, onDismiss: { Task { await share.load() } }) {
                NavigationStack { CardComposerPage(account: share.account) }
            }
            .sheet(item: $received, onDismiss: { scanRound += 1 }) { link in
                NavigationStack { ReceivedSharePage(model: ReceivedShareModel(link: link, account: share.account)) }
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
            ZStack {
                switch camera {
                case .allowed?: QRCameraView { scanned($0) }.id(scanRound)
                case .denied?: cameraNotice("카메라를 쓸 수 없어요", "설정에서 Dearby의 카메라 접근을 켜거나 사진에서 스캔해 주세요.")
                case .unavailable?: cameraNotice("카메라가 없어요", "사진에서 스캔해 주세요.")
                case nil: ProgressView().tint(.white)
                }
            }.frame(maxWidth: .infinity, minHeight: 370).background(Color(white: 0.2))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .accessibilityElement(children: .contain).accessibilityLabel("QR 스캔 화면")
            PhotosPicker(selection: $photo, matching: .images) {
                Label("사진에서 스캔", systemImage: "photo").frame(maxWidth: .infinity)
            }.buttonStyle(DearbyButtonStyle(outlined: true))
            if let scanError { Text(scanError).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink) }
            Text("Dearby 명함 QR을 비추면 바로 열려요.").font(.caption).foregroundStyle(DearbyStyle.quiet)
        }
        .task {
            camera = await QRCameraView.requestAccess()
        }
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task {
                let data = try? await item.loadTransferable(type: Data.self)
                photo = nil
                if let text = data.flatMap(QRImageReader.text) { scanned(text) } else { scanError = "사진에서 QR을 찾지 못했어요." }
            }
        }
    }
    private func cameraNotice(_ title: String, _ detail: String) -> some View {
        VStack(spacing: 8) {
            Text(title).font(.dearby(.headline))
            Text(detail).font(.dearby(.subheadline)).multilineTextAlignment(.center)
        }.foregroundStyle(.white).padding(24)
    }
    private func scanned(_ text: String) {
        if let link = parse(text) { scanError = nil; received = link } else { scanError = "Dearby 명함 QR이 아니에요." }
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
