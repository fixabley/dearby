import GoogleMobileAds
import SwiftUI

/// 맞춤 대기열 끝 광고 (contract "맞춤 대기열 끝 광고(AdMob, 비개인화)" 2026-10-25): one AdMob native ad per queue opening, loaded when the queue
/// opens. Requests are non-personalised (`npa=1`, personalisation state disabled) and capped at 부모 지도(PG); no ATT prompt, no UMP (Korea only).
/// The unit comes from the build (Info.plist `DearbyAdMobNativeUnitID`): Google's native test unit in Debug, and empty in a Release build without
/// AdMob IDs, where the SDK is never started and the queue has no ad page.
@MainActor @Observable final class QueueAdLoader: NSObject, NativeAdLoaderDelegate {
    static let enabled = !unitID.isEmpty
    private static let unitID = Bundle.main.object(forInfoDictionaryKey: "DearbyAdMobNativeUnitID") as? String ?? ""
    private static var started = false
    private(set) var nativeAd: NativeAd?
    @ObservationIgnored private var loader: AdLoader?

    /// One request per loader (the queue page keeps one for its opening); a failure leaves `nativeAd` nil.
    func load() {
        guard Self.enabled, loader == nil else { return }
        if !Self.started {
            Self.started = true
            let configuration = MobileAds.shared.requestConfiguration
            configuration.maxAdContentRating = .parentalGuidance
            configuration.publisherPrivacyPersonalizationState = .disabled
            MobileAds.shared.start(completionHandler: nil)
        }
        let loader = AdLoader(adUnitID: Self.unitID, rootViewController: nil, adTypes: [.native], options: nil)
        loader.delegate = self
        let extras = Extras()
        extras.additionalParameters = ["npa": "1"]
        let request = Request()
        request.register(extras)
        loader.load(request)
        self.loader = loader
    }
    func adLoader(_ adLoader: AdLoader, didReceive nativeAd: NativeAd) { self.nativeAd = nativeAd }
    func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: Error) {}
}

/// The ad as a full-screen story page: `광고` badge with AdChoices at the top, then the media, advertiser, headline, body and call to action.
/// Every asset is a UIKit view registered on the SDK's `NativeAdView` and shown as given (native ads policy); the SDK handles the tap.
struct QueueAdView: UIViewRepresentable {
    let nativeAd: NativeAd
    func makeUIView(context: Context) -> StoryNativeAdView { StoryNativeAdView() }
    func updateUIView(_ view: StoryNativeAdView, context: Context) {
        if view.nativeAd !== nativeAd { view.show(nativeAd) }
    }
}

final class StoryNativeAdView: NativeAdView {
    private let advertiser = StoryNativeAdView.label(.footnote, "Pretendard-SemiBold", lines: 1, alpha: 0.8)
    private let headline = StoryNativeAdView.label(.title3, "Pretendard-Bold", lines: 2, alpha: 1)
    private let bodyText = StoryNativeAdView.label(.subheadline, "Pretendard-Regular", lines: 3, alpha: 0.85)
    private let action = UIButton(configuration: .filled())
    private let media = MediaView()
    private var mediaRatio: NSLayoutConstraint?

    init() {
        super.init(frame: .zero)
        backgroundColor = .black
        let badge = UILabel()
        badge.text = "광고"
        badge.font = UIFontMetrics(forTextStyle: .caption1).scaledFont(for: UIFont(name: "Pretendard-SemiBold", size: 12) ?? .preferredFont(forTextStyle: .caption1))
        badge.adjustsFontForContentSizeCategory = true
        badge.textColor = .black
        let badgeBox = UIView()
        badgeBox.backgroundColor = UIColor(white: 1, alpha: 0.9)
        badgeBox.layer.cornerRadius = 4
        badgeBox.addSubview(badge)
        badge.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            badge.leadingAnchor.constraint(equalTo: badgeBox.leadingAnchor, constant: 6), badge.trailingAnchor.constraint(equalTo: badgeBox.trailingAnchor, constant: -6),
            badge.topAnchor.constraint(equalTo: badgeBox.topAnchor, constant: 2), badge.bottomAnchor.constraint(equalTo: badgeBox.bottomAnchor, constant: -2)
        ])
        let choices = AdChoicesView()
        let header = UIStackView(arrangedSubviews: [badgeBox, choices, UIView()])
        header.spacing = 8
        header.alignment = .center
        media.contentMode = .scaleAspectFit
        var button = UIButton.Configuration.filled()
        button.baseBackgroundColor = UIColor(red: 0, green: 127.0 / 255, blue: 128.0 / 255, alpha: 1)
        button.baseForegroundColor = .white
        button.cornerStyle = .capsule
        button.titleLineBreakMode = .byTruncatingTail
        action.configuration = button
        // The SDK takes the tap on the call to action.
        action.isUserInteractionEnabled = false
        action.heightAnchor.constraint(greaterThanOrEqualToConstant: 50).isActive = true
        let above = UIView(), below = UIView()
        let stack = UIStackView(arrangedSubviews: [header, above, media, below, advertiser, headline, bodyText, action])
        stack.axis = .vertical
        stack.spacing = 10
        stack.setCustomSpacing(16, after: bodyText)
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 18), stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -18),
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 14), stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -24),
            above.heightAnchor.constraint(equalTo: below.heightAnchor),
            media.heightAnchor.constraint(greaterThanOrEqualToConstant: 120)
        ])
        headlineView = headline
        advertiserView = advertiser
        bodyView = bodyText
        callToActionView = action
        mediaView = media
        adChoicesView = choices
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }

    /// Fills the assets, then hands the ad to the SDK (which needs the views populated first).
    func show(_ ad: NativeAd) {
        headline.text = ad.headline
        advertiser.text = ad.advertiser
        advertiser.isHidden = ad.advertiser == nil
        bodyText.text = ad.body
        bodyText.isHidden = ad.body == nil
        action.configuration?.title = ad.callToAction
        action.isHidden = ad.callToAction == nil
        media.mediaContent = ad.mediaContent
        mediaRatio?.isActive = false
        let ratio = ad.mediaContent.aspectRatio > 0 ? ad.mediaContent.aspectRatio : 16.0 / 9.0
        mediaRatio = media.heightAnchor.constraint(equalTo: media.widthAnchor, multiplier: 1 / ratio)
        mediaRatio?.priority = .defaultHigh
        mediaRatio?.isActive = true
        nativeAd = ad
    }
    private static func label(_ style: UIFont.TextStyle, _ font: String, lines: Int, alpha: CGFloat) -> UILabel {
        let label = UILabel()
        let base = UIFont.preferredFont(forTextStyle: style, compatibleWith: UITraitCollection(preferredContentSizeCategory: .large))
        label.font = UIFontMetrics(forTextStyle: style).scaledFont(for: UIFont(name: font, size: base.pointSize) ?? base)
        label.adjustsFontForContentSizeCategory = true
        label.textColor = UIColor(white: 1, alpha: alpha)
        label.numberOfLines = lines
        return label
    }
}
