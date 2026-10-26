import AppTrackingTransparency
import GoogleMobileAds
import SwiftUI

/// 맞춤 대기열 끝 광고 (contract native-v1.md "맞춤 대기열 끝 광고(AdMob, 비개인화)" 2026-10-25): one AdMob native ad (media ratio ANY, the default)
/// per queue opening, requested once after the queue arrives and never again; the page owns the loader, so closing the queue drops the ad.
/// Requests are non-personalised (`npa=1`, publisher first-party ID off) and capped at PG whatever the answer to the ATT prompt (2026-10-25 user
/// decision: asked once, before the first request of an ad-enabled build, never at launch; frequency capping and measurement only), no UMP
/// (Korea only). The unit comes from the build (Info.plist `DearbyAdMobNativeUnitID`): Google's native test
/// unit in Debug, and empty in a Release build without AdMob IDs, where the SDK is never started and the queue has no ad page.
/// `enabled` also needs the build flag `DearbyAdsEnabled` (`DEARBY_ADS_ENABLED`, off by default for the initial test period, 2026-10-26): off, the
/// ad page, the consent sheet, the ATT prompt and the 설정 consent switch are all absent while the code and the SDK stay in the build.
@MainActor @Observable final class QueueAdLoader: NSObject, NativeAdLoaderDelegate {
    static let enabled = !unitID.isEmpty && Bundle.main.object(forInfoDictionaryKey: "DearbyAdsEnabled") as? String == "true"
    private static let unitID = Bundle.main.object(forInfoDictionaryKey: "DearbyAdMobNativeUnitID") as? String ?? ""
    private(set) var nativeAd: NativeAd?
    /// The request failed (사용 통계 `queue_ad_skipped_unloaded` reason `failed`, else `loading`).
    private(set) var failed = false
    @ObservationIgnored private var loader: AdLoader?
    /// `load()` ran (사용 통계 `queue_ad_skipped_unloaded` only counts openings that asked for an ad).
    @ObservationIgnored private(set) var requested = false

    /// Call only once the account agreed (`AdConsentModel.queueAnswer`, contract "광고 목적 개인정보 제3자 제공 동의(선택)"): this starts the
    /// SDK, which nothing else does (Info.plist `GADDelayAppMeasurementInit` keeps its measurement off until then).
    /// One request per loader; a failure leaves `nativeAd` nil. The first time (ATT `notDetermined`, app active so the system sheet shows) it
    /// asks for tracking and waits for the answer; the request goes ahead whatever it is.
    func load() async {
        guard Self.enabled, !requested else { return }
        requested = true
        await Self.start()
        request()
    }
    /// Agreed at the queue's end, after the ad's place had passed: ATT and the SDK start now, the ad waits for the next opening.
    static func startAfterConsent() async {
        guard enabled else { return }
        await start()
    }
    /// The ATT prompt the first time (`notDetermined`, app active so the system sheet shows), then the SDK. Starting does not wait for it:
    /// a request made meanwhile is queued by the SDK.
    private static func start() async {
        if ATTrackingManager.trackingAuthorizationStatus == .notDetermined, UIApplication.shared.applicationState == .active {
            _ = await ATTrackingManager.requestTrackingAuthorization()
        }
        let configuration = MobileAds.shared.requestConfiguration
        configuration.maxAdContentRating = .parentalGuidance
        configuration.setPublisherFirstPartyIDEnabled(false)
        // Later calls return at once; the first starts the SDK.
        MobileAds.shared.start(completionHandler: nil)
    }
    private func request() {
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
    func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: Error) { failed = true }
}

/// The ad as a full-screen story page: a dim single progress segment (apart from the activities' scene bars), the `광고` badge with AdChoices
/// in the top-left corner (away from the queue's close button and the call to action), then the media in the space left, advertiser, headline,
/// body and call to action. No auto-advance, tap zones or button rail: the queue's vertical swipe and close button move on. Every asset is a
/// UIKit view registered on the SDK's `NativeAdView` and shown as given (native ads policy); the SDK handles the tap.
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

    init() {
        super.init(frame: .zero)
        backgroundColor = .black
        let badge = UILabel()
        badge.text = "광고"
        badge.font = UIFontMetrics(forTextStyle: .subheadline).scaledFont(for: UIFont(name: "Pretendard-SemiBold", size: 15) ?? .preferredFont(forTextStyle: .subheadline))
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
        let segment = UIView()
        segment.backgroundColor = UIColor(white: 1, alpha: 0.35)
        segment.layer.cornerRadius = 1.5
        segment.heightAnchor.constraint(equalToConstant: 3).isActive = true
        media.contentMode = .scaleAspectFit
        media.setContentHuggingPriority(.defaultLow - 1, for: .vertical)
        media.setContentCompressionResistancePriority(.defaultLow - 1, for: .vertical)
        var button = UIButton.Configuration.filled()
        button.baseBackgroundColor = UIColor(DearbyStyle.tealFill)
        button.baseForegroundColor = .white
        button.cornerStyle = .capsule
        button.titleLineBreakMode = .byTruncatingTail
        action.configuration = button
        // The SDK takes the tap on the call to action.
        action.isUserInteractionEnabled = false
        action.heightAnchor.constraint(greaterThanOrEqualToConstant: 50).isActive = true
        let stack = UIStackView(arrangedSubviews: [segment, header, media, advertiser, headline, bodyText, action])
        stack.axis = .vertical
        stack.spacing = 10
        stack.setCustomSpacing(16, after: header)
        stack.setCustomSpacing(16, after: media)
        stack.setCustomSpacing(16, after: bodyText)
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 18), stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -18),
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 8), stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -24),
            media.heightAnchor.constraint(greaterThanOrEqualToConstant: 120)
        ])
        headlineView = headline
        advertiserView = advertiser
        bodyView = bodyText
        callToActionView = action
        mediaView = media
        adChoicesView = choices
        // VoiceOver: the badge first, then headline, body and call to action.
        accessibilityElements = [badge, headline, bodyText, action, advertiser, media, choices]
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
