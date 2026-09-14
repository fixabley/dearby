import SwiftUI

/// App content metrics; system controls retain their own native metrics.
enum NativeSpacing {
    static let compact: CGFloat = 4
    static let related: CGFloat = 8
    static let content: CGFloat = 16
    static let section: CGFloat = 24
}

/// Semantic UIKit backgrounds adapt to appearance and increased contrast.
enum NativeSurface {
    static let canvas = Color(uiColor: .systemGroupedBackground)
    static let content = Color(uiColor: .secondarySystemGroupedBackground)
}
