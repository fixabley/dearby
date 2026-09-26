import SwiftUI
import CoreImage.CIFilterBuiltins
import PhotosUI
import Vision
import Photos

struct QRPage: View {
    let cards: [CardModel]
    let incomingURL: URL?
    @Binding var selectedID: String?
    let create: () -> Void
    let lookup: (String) async throws -> CardModel
    let saveGuest: (String, ExchangeContextModel) throws -> Void
    @ScaledMetric(relativeTo: .caption2) private var nameSize = 10.0
    @ScaledMetric(relativeTo: .caption2) private var descriptionSize = 8.0
    @State private var mode = 0
    @State private var input = ""
    @State private var activity = ""
    @State private var receivedContext = ExchangeContextModel()
    @State private var message: String?
    @State private var enlarged: QRPresentation?
    @State private var detail: CardModel?
    @State private var pendingSave: CardModel?
    @State private var busy = false
    @State private var scanning = false
    @State private var photo: PhotosPickerItem?
    private var selected: CardModel? { cards.first { $0.id == selectedID } }
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Picker("QR", selection: $mode) { Text("보여주기").tag(0); Text("찍기").tag(1) }.pickerStyle(.segmented)
                if mode == 0 { display } else { receive }
                if let message { Text(message).font(.footnote).accessibilityAddTraits(.updatesFrequently) }
            }.padding()
        }.navigationTitle("QR")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) {
                if mode == 0 {
                    VStack(spacing: 8) {
                        cardPicker
                        if selected != nil {
                            DisclosureGroup("교환한 활동 (선택)") {
                                TextField("활동 이름", text: $activity).textFieldStyle(.roundedBorder)
                                Text("활동 입력은 실제 참가 인증을 뜻하지 않습니다.").font(.caption2).foregroundStyle(.secondary)
                            }.font(.caption)
                        }
                    }.padding(.horizontal).padding(.vertical, 8).background(.bar)
                }
            }
            .fullScreenCover(item: $enlarged) { presentation in
                ZStack {
                    Color.white.ignoresSafeArea()
                    Image(uiImage: presentation.image).interpolation(.none).resizable().scaledToFit().padding(28)
                }.contentShape(Rectangle()).onTapGesture { enlarged = nil }
                    .accessibilityLabel("QR 확대. 두 번 탭하여 닫기").accessibilityAddTraits(.isButton)
                    .statusBarHidden()
            }
            .sheet(isPresented: $scanning) {
                CameraScanner { payload in input = payload }
            }
            .onChange(of: scanning) { old, new in
                if old && !new && !input.isEmpty { Task { await inspect() } }
            }
            .sheet(item: $detail) { card in
                NavigationStack { ScrollView { CardView(card: card, onContact: ContactActions.perform).padding() }
                    .toolbar { Button("닫기") { detail = nil } } }
            }
            .confirmationDialog("이 명함을 기기에 저장할까요?", isPresented: Binding(
                get: { pendingSave != nil }, set: { if !$0 { pendingSave = nil } }), titleVisibility: .visible) {
                    Button("기기에 저장") {
                        guard let card = pendingSave else { return }
                        do {
                            try saveGuest(card.id, receivedContext)
                            message = "\(card.profileName)님의 명함을 기기에 저장했습니다."
                        } catch { message = error.localizedDescription }
                        pendingSave = nil
                    }
                    Button("취소", role: .cancel) { pendingSave = nil }
                } message: { Text("로그인하지 않고 저장한 명함은 앱을 삭제하면 복구할 수 없어요. 로그인 후 선택하여 계정으로 가져올 수 있습니다.") }
            .task(id: incomingURL) {
                if let incomingURL { mode = 1; input = incomingURL.absoluteString; await inspect() }
            }
            .onChange(of: photo) { _, item in
                Task {
                    do {
                        guard let data = try await item?.loadTransferable(type: Data.self) else { return }
                        let request = VNDetectBarcodesRequest()
                        request.symbologies = [.qr]
                        try VNImageRequestHandler(data: data).perform([request])
                        guard let payload = request.results?.first?.payloadStringValue else { throw CocoaError(.coderReadCorrupt) }
                        input = payload
                        await inspect()
                    } catch { message = "QR을 읽을 수 없습니다. 명함 링크를 직접 붙여넣어 주세요." }
                }
            }
    }
    @ViewBuilder private var display: some View {
        if let selected {
            VStack(spacing: 8) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(selected.name).font(.system(size: nameSize, weight: .semibold))
                        Text(selected.description).font(.system(size: descriptionSize)).foregroundStyle(.secondary)
                    }
                    Spacer()
                    shareMenu(for: selected)
                }
                if let url = shareURL(selected.id), let image = qrImage(url.absoluteString) {
                    Button { enlarged = QRPresentation(image: image) } label: {
                        Image(uiImage: image).interpolation(.none).resizable().scaledToFit().frame(maxWidth: 300)
                    }.accessibilityLabel("QR만 크게 보기")
                } else {
                    Text("활동 이름은 200자 이하로 입력해 주세요.").font(.footnote)
                }
            }.padding(16).frame(maxWidth: .infinity)
                .background(.white, in: RoundedRectangle(cornerRadius: 24))
                .overlay(RoundedRectangle(cornerRadius: 24).stroke(.teal.opacity(0.25)))
            Label("QR을 누르면 QR만 크게 보여요.", systemImage: "info.circle")
                .font(.footnote).foregroundStyle(.secondary)
            Button("명함 보기") { detail = selected }.buttonStyle(.bordered)
        } else {
            VStack(spacing: 20) {
                Image(systemName: "person.text.rectangle").font(.system(size: 56)).foregroundStyle(.teal)
                Text("이번에 공유할 명함을 만드세요").font(.title2.bold())
                Text("공개할 연락처와 활동 이력을 골라 나를 소개하세요.").foregroundStyle(.secondary)
                Button("새 명함 만들기", action: create).buttonStyle(.borderedProminent)
            }.frame(maxWidth: .infinity).padding(.vertical, 70)
                .padding(.horizontal).background(.teal.opacity(0.06), in: RoundedRectangle(cornerRadius: 24))
        }
    }
    @ViewBuilder private func shareMenu(for card: CardModel) -> some View {
        if let url = shareURL(card.id), let image = qrImage(url.absoluteString) {
            Menu {
                ShareLink(item: url) { Label("링크 공유", systemImage: "square.and.arrow.up") }
                Button("링크 복사", systemImage: "doc.on.doc") { UIPasteboard.general.url = url; message = "링크를 복사했습니다." }
                Button("QR 이미지 저장", systemImage: "square.and.arrow.down") { Task { await saveImage(image) } }
                Text("Dearby가 설치된 기기에서 열 수 있어요.")
            } label: { Image(systemName: "square.and.arrow.up").frame(minWidth: 44, minHeight: 44) }
                .accessibilityLabel("명함 공유")
        }
    }
    private var cardPicker: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 10) {
                Button { selectedID = nil } label: {
                    VStack(spacing: 6) {
                        Image(systemName: "plus").font(.title3)
                        Text("새 명함").font(.caption)
                    }.frame(width: 84, height: 76)
                        .background(.teal.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(.teal.opacity(0.3)))
                }.buttonStyle(.plain).accessibilityLabel("새 명함")
                ForEach(cards) { card in
                    Button { selectedID = card.id } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(card.profileName).font(.caption.bold()).foregroundStyle(.primary)
                            Text(card.job).font(.caption2).foregroundStyle(.secondary)
                            Text(card.name).font(.caption2).foregroundStyle(.teal)
                        }.lineLimit(1).frame(width: 130, height: 76, alignment: .leading).padding(.horizontal, 12)
                            .background(selected?.id == card.id ? Color.teal.opacity(0.08) : .white,
                                in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(
                                selected?.id == card.id ? .teal : .gray.opacity(0.25), lineWidth: selected?.id == card.id ? 2 : 1))
                    }.buttonStyle(.plain).accessibilityLabel(card.name)
                        .accessibilityHint(card.profileName + " · " + card.job)
                        .accessibilityAddTraits(selected?.id == card.id ? .isSelected : [])
                }
            }.padding(2)
        }.scrollIndicators(.hidden)
    }
    private var receive: some View {
        VStack(spacing: 20) {
            Text("명함 QR을 불러오세요").font(.title2.bold())
            PhotosPicker(selection: $photo, matching: .images) { Label("사진에서 QR 읽기", systemImage: "photo") }
                .buttonStyle(.borderedProminent)
            Button("카메라로 찍기", systemImage: "camera") { input = ""; scanning = true }.buttonStyle(.bordered)
            TextField("명함 링크 붙여넣기", text: $input).textInputAutocapitalization(.never)
                .autocorrectionDisabled().textFieldStyle(.roundedBorder)
            TextField("교환한 활동 (선택)", text: $activity).textFieldStyle(.roundedBorder)
            Button(busy ? "명함 확인 중…" : "명함 확인하고 저장") { Task { await inspect() } }
                .buttonStyle(.borderedProminent).disabled(busy || input.isEmpty)
        }
    }
    private func saveImage(_ image: UIImage) async {
        let permission = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard permission == .authorized || permission == .limited else {
            message = "사진 저장 권한이 없습니다. 설정에서 허용하거나 링크 공유를 이용해 주세요."; return
        }
        do {
            try await PHPhotoLibrary.shared().performChanges { PHAssetChangeRequest.creationRequestForAsset(from: image) }
            message = "QR 이미지를 사진에 저장했습니다."
        } catch { message = "QR 이미지를 저장하지 못했습니다." }
    }
    private func inspect() async {
        busy = true
        defer { busy = false }
        let raw = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let id: String
        if UUID(uuidString: raw) != nil {
            id = raw; receivedContext = ExchangeContextModel(label: activity.isEmpty ? nil : activity)
        } else if let url = URL(string: raw), let parsed = try? CardLink(parsing: url) {
            id = parsed.cardID; receivedContext = parsed.context
        } else if let url = URL(string: raw), let base = configuredShareURL,
                url.scheme == base.scheme, url.host == base.host,
                url.path.hasPrefix(base.path + "/"), UUID(uuidString: url.lastPathComponent) != nil {
            id = url.lastPathComponent; receivedContext = ExchangeContextModel(label: activity.isEmpty ? nil : activity)
        } else { message = "올바른 Dearby 명함 링크 붙여넣기를 입력해 주세요."; return }
        do { pendingSave = try await lookup(id) } catch { message = error.localizedDescription }
    }
    private var configuredShareURL: URL? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "DearbyShareURL") as? String,
              let base = URL(string: raw), base.scheme == "https", base.host != nil else { return nil }
        return base
    }
    private func shareURL(_ id: String) -> URL? {
        guard activity.count <= 200 else { return nil }
        return configuredShareURL?.appendingPathComponent(id) ?? CardLink(cardID: id,
            context: ExchangeContextModel(label: activity.isEmpty ? nil : activity)).url
    }
    private func qrImage(_ value: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(value.utf8)
        guard let code = filter.outputImage else { return nil }
        let quietZone = CIImage(color: CIColor(red: 1, green: 1, blue: 1)).cropped(to: code.extent.insetBy(dx: -4, dy: -4))
        let output = code.composited(over: quietZone).transformed(by: CGAffineTransform(scaleX: 12, y: 12))
        guard let image = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: image)
    }
}
private struct QRPresentation: Identifiable { let id = UUID(); let image: UIImage }
