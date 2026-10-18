import CoreImage.CIFilterBuiltins
import SwiftUI

/// 내 명함 QR (contract "명함 교환" 2026-10-19): the card's public web address with the activity it is handed out at,
/// drawn with CoreImage. The activity chip starts on the nearest ongoing applied activity and can switch to another
/// applied one or `활동 없이`. The screen goes to full brightness while the sheet is up and comes back on dismiss.
struct CardQRSheet: View {
    let cardName: String
    let cardID: String
    let webOrigin: URL
    /// Applied activities, the one to preselect first; empty hides the chip.
    let activities: [ActivityModel]
    @State private var activity: ActivityModel?
    @State private var brightnessBefore: CGFloat?
    @Environment(\.dismiss) private var dismiss
    init(cardName: String, cardID: String, webOrigin: URL, activities: [ActivityModel]) {
        self.cardName = cardName
        self.cardID = cardID
        self.webOrigin = webOrigin
        self.activities = activities
        _activity = State(initialValue: activities.first)
    }
    /// `https://<web>/cards/<id>?activity=<n>`; no query without an activity.
    var link: URL {
        var parts = URLComponents(url: webOrigin.appending(path: "cards/\(cardID)"), resolvingAgainstBaseURL: false) ?? URLComponents()
        parts.queryItems = activity.map { [URLQueryItem(name: "activity", value: $0.id)] }
        return parts.url ?? webOrigin
    }
    var body: some View {
        VStack(spacing: 20) {
            Spacer(minLength: 0)
            if let image = Self.qrImage(link.absoluteString) {
                Image(uiImage: image).interpolation(.none).resizable().scaledToFit().frame(maxWidth: 280)
                    .padding(16).background(.white, in: RoundedRectangle(cornerRadius: 20))
                    .overlay(RoundedRectangle(cornerRadius: 20).stroke(DearbyStyle.line))
                    .accessibilityLabel("내 명함 QR 코드, 카메라로 읽으면 명함을 받아요")
            } else {
                Text("QR을 만들지 못했어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
            }
            Text(cardName).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink)
            if !activities.isEmpty { chip }
            Text(activity == nil ? "받는 사람의 받은 명함에 저장돼요." : "받는 사람의 받은 명함에 이 활동에서 받은 것으로 저장돼요.")
                .font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
            Spacer(minLength: 0)
        }
        .padding(20).frame(maxWidth: .infinity, maxHeight: .infinity).background(.white)
        .navigationTitle("QR 보이기").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        .onAppear(perform: brighten).onDisappear(perform: restore)
    }
    /// The activity chip is a menu: the applied activities and `활동 없이`.
    private var chip: some View {
        Menu {
            ForEach(activities) { item in
                Button { activity = item } label: {
                    if item.id == activity?.id { Label(item.title, systemImage: "checkmark") } else { Text(item.title) }
                }
            }
            Button { activity = nil } label: {
                if activity == nil { Label("활동 없이", systemImage: "checkmark") } else { Text("활동 없이") }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "calendar")
                Text(activity.map { [$0.program, $0.title].compactMap { $0 }.joined(separator: " ") } ?? "활동 없이").lineLimit(1)
                Image(systemName: "chevron.down").font(.dearby(.caption))
            }
            .font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
            .padding(.horizontal, 14).padding(.vertical, 8).background(DearbyStyle.mint, in: Capsule())
        }.accessibilityLabel("활동 고르기, 지금 \(activity?.title ?? "활동 없이")")
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
