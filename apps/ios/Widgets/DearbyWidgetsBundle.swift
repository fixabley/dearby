import SwiftUI
import WidgetKit

/// `DearbyWidgets` (contract "행사 당일 진행 중 표시 — 2026-10-26 사용자 결정"): the extension that draws the app's Live Activity. No home-screen widgets.
@main struct DearbyWidgetsBundle: WidgetBundle {
    var body: some Widget {
        LiveSessionWidget()
    }
}
