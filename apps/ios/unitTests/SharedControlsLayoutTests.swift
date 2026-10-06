import SwiftUI
import XCTest
@testable import Dearby

/// 공통 입력 컴포넌트가 390pt 폭을 넘지 않는지 확인하고, 요청 시 검토용 캡처를 남긴다.
@MainActor final class SharedControlsLayoutTests: XCTestCase {
    func testControlsFitPhoneWidth() throws {
        for (name, view) in [("edit", AnyView(DearbyControlsGallery())), ("read", AnyView(DearbyControlsGallery(editing: false))),
                             ("received-groups", AnyView(ReceivedCardGroupsSample())),
                             ("qr-share", AnyView(QRShareSample())), ("qr-share-loading", AnyView(QRShareSample(url: nil))),
                             ("qr-share-error", AnyView(QRShareSample(url: nil, errorMessage: "공유를 만들지 못했어요."))),
                             ("qr-scan", AnyView(QRScanOverlay(scanFromPhotos: {}) { Color.gray }.frame(width: 390, height: 560))),
                             ("card-composer", AnyView(CardComposerSample().frame(width: 390, height: 844))),
                             ("card-composer-empty", AnyView(CardComposerSample(name: "").frame(width: 390, height: 844))),
                             ("activity-quick-apply", AnyView(ActivityCardSample())),
                             ("apply-confirmation", AnyView(ApplyConfirmationSheet(activityTitle: "주말 데이터 분석 스터디", applied: {}, notYet: {}, neverAsk: {}).background(.white)))] {
            let host = UIHostingController(rootView: view)
            host.safeAreaRegions = []
            let fitted = host.sizeThatFits(in: CGSize(width: 390, height: CGFloat.greatestFiniteMagnitude))
            // 3배 화면 픽셀 반올림(1/3pt)까지는 넘침으로 보지 않는다.
            XCTAssertLessThanOrEqual(fitted.width, 390 + 1.0 / 3 + 0.01, name)
            XCTAssertGreaterThan(fitted.height, 0)
            guard let directory = ProcessInfo.processInfo.environment["DEARBY_CAPTURE_DIR"] else { continue }
            let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
            let window = UIWindow(windowScene: scene)
            window.frame = CGRect(origin: .zero, size: CGSize(width: 390, height: fitted.height))
            window.rootViewController = host
            window.makeKeyAndVisible()
            RunLoop.main.run(until: Date().addingTimeInterval(0.5))
            let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            let file = URL(fileURLWithPath: directory).appendingPathComponent("ios-\(name).png")
            try XCTUnwrap(image.pngData()).write(to: file)
        }
    }
}
