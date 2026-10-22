import CoreImage.CIFilterBuiltins
import SwiftUI

/// 내 명함 QR (contract "명함 교환" 2026-10-19, "명함 QR 코드" 2026-10-22): the card's web address with the activity it is handed
/// out at and a code many people can use until it expires, drawn with CoreImage. The code is the prefetched one or issued on open,
/// replaced by the refresh button or by itself 5 minutes before its 30-minute expiry, and expired on close. No code, no QR. The activity menu starts on the nearest ongoing applied activity and can switch to another
/// applied one or `활동 없이`. The screen goes to full brightness while the sheet is up and comes back on dismiss.
/// Centred message layout (contract "비스크롤 화면 배치·CTA"): the QR is the glyph, the card name the title. The white padding
/// around the code is the scanner's quiet zone, not a card, so it stays while the border goes.
struct CardQRSheet: View {
    let cardName: String
    let cardID: String
    let cards: MyCardsModel
    /// Applied activities, the one to preselect first; empty hides the menu.
    let activities: [ActivityModel]
    @State private var activityID: String?
    @State private var brightnessBefore: CGFloat?
    @State private var code: AccountClient.QRCode?
    @State private var issueFailed = false
    /// Bumped by the refresh button and the auto-refresh; each value runs one `rotate`.
    @State private var generation = 0
    @Environment(\.dismiss) private var dismiss
    init(cardName: String, cardID: String, cards: MyCardsModel, activities: [ActivityModel]) {
        self.cardName = cardName
        self.cardID = cardID
        self.cards = cards
        self.activities = activities
        _activityID = State(initialValue: activities.first?.id)
    }
    private var activity: ActivityModel? { activities.first { $0.id == activityID } }
    /// `https://<web>/cards/<id>?activity=<n>&code=<code>`; no `activity` without an activity.
    func link(_ code: String) -> URL? {
        var parts = URLComponents(url: cards.webOrigin.appending(path: "cards/\(cardID)"), resolvingAgainstBaseURL: false)
        parts?.queryItems = (activity.map { [URLQueryItem(name: "activity", value: $0.id)] } ?? []) + [URLQueryItem(name: "code", value: code)]
        return parts?.url
    }
    var body: some View {
        CenteredMessage(title: cardName, message: activity == nil ? "받는 사람의 받은 명함에 저장돼요." : "받는 사람의 받은 명함에 이 활동에서 받은 것으로 저장돼요.") {
            VStack(spacing: 16) {
                if let code {
                    qr(code.code)
                } else if issueFailed {
                    StateGlyph(symbol: "wifi.exclamationmark", danger: true)
                    Text("연결을 확인하고 다시 시도해 주세요").font(.dearby(.body)).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    Button("다시 시도") { generation += 1 }.buttonStyle(.bordered)
                } else {
                    ProgressView().controlSize(.large).frame(width: 280, height: 280)
                }
                if !activities.isEmpty { activityMenu }
            }
        } actions: {
            Button { dismiss() } label: { Text("닫기").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
        }
        .background(Color(.systemBackground))
        .navigationTitle("QR 보이기").navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } }
            ToolbarItem(placement: .primaryAction) {
                Button { generation += 1 } label: { Image(systemName: "arrow.clockwise") }
                    .accessibilityLabel("QR 새로 만들기").disabled(code == nil && !issueFailed)
            }
        }
        .task(id: generation) { await rotate() }
        .onAppear(perform: brighten)
        .onDisappear {
            restore()
            // Closing never waits for the server; it keeps the code 2 more minutes, and the 30-minute expiry covers a failure.
            if let code { cards.expireQRCode(cardID, codeID: code.id) }
        }
    }
    @ViewBuilder private func qr(_ code: String) -> some View {
                if let link = link(code), let image = Self.qrImage(link.absoluteString) {
                    Image(uiImage: image).interpolation(.none).resizable().scaledToFit().frame(maxWidth: 280)
                        .padding(16).background(.white, in: .rect(cornerRadius: 20, style: .continuous))
                        .accessibilityLabel("내 명함 QR 코드, 카메라로 읽으면 명함을 받아요")
                } else {
                    StateGlyph(symbol: "qrcode", danger: true)
                    Text("QR을 만들지 못했어요.").font(.dearby(.body)).foregroundStyle(.secondary)
                }
    }
    /// Opening uses the prefetched code when it still has over 5 minutes; a refresh issues a new one and then expires the old.
    /// A failure drops the QR for the retry state. The task sleeps until 5 minutes before expiry, then bumps `generation` itself.
    private func rotate() async {
        let old = code
        issueFailed = false
        let fresh = old == nil ? await cards.openingQRCode(cardID) : await cards.issueQRCode(cardID)
        if Task.isCancelled {
            // A newer refresh took over while this one was in flight; this code is never shown.
            if let fresh, fresh != old { cards.expireQRCode(cardID, codeID: fresh.id) }
            return
        }
        if let old { cards.expireQRCode(cardID, codeID: old.id) }
        code = fresh
        guard let fresh else { issueFailed = true; return }
        let wait = (fresh.expiry?.timeIntervalSinceNow ?? 1800) - 300
        try? await Task.sleep(for: .seconds(max(wait, 30)))
        if !Task.isCancelled { generation += 1 }
    }
    /// The activity choice is a system menu picker (the check mark on the chosen row is the system's): the applied activities and `활동 없이`.
    private var activityMenu: some View {
        Picker(selection: $activityID) {
            ForEach(activities) { item in Text(item.title).tag(Optional(item.id)) }
            Text("활동 없이").tag(nil as String?)
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "calendar")
                Text(activity.map { [$0.program, $0.title].compactMap { $0 }.joined(separator: " ") } ?? "활동 없이").lineLimit(1)
            }
        }
        .pickerStyle(.menu).tint(DearbyStyle.teal).font(.dearby(.subheadline).weight(.semibold))
        .accessibilityLabel("활동 고르기, 지금 \(activity?.title ?? "활동 없이")")
    }
    private var screen: UIScreen? {
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.screen }.first
    }
    private func brighten() {
        guard let screen, brightnessBefore == nil else { return }
        brightnessBefore = screen.brightness
        screen.brightness = 1
    }
    private func restore() {
        if let before = brightnessBefore { screen?.brightness = before }
        brightnessBefore = nil
    }
    /// A black-on-white QR at a crisp scale; nil only if CoreImage fails to render.
    static func qrImage(_ text: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)),
              let cgImage = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
