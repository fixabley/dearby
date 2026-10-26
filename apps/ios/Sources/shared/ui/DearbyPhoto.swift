import SwiftUI
import UIKit

/// Reads a `photoUrl` (contract "프로필 사진" 2026-10-26) with the app's authorised HTTP client; `AsyncImage` cannot send the Bearer token.
/// The app puts one in the environment; without it photos stay initials.
struct PhotoLoader: Sendable {
    let load: @Sendable (String) async throws -> Data
}
extension EnvironmentValues {
    @Entry var photoLoader: PhotoLoader?
}

/// A round photo, or `DearbyAvatar` initials while there is none, it loads or it failed. The URL carries `?v=<ms>`, so one URL is one image
/// for the app run (`cache`).
struct DearbyPhotoAvatar: View {
    let photoUrl: String?
    let name: String
    var filled = false
    var size: CGFloat = 76
    @Environment(\.photoLoader) private var loader
    @State private var image: UIImage?
    @MainActor private static let cache = NSCache<NSString, UIImage>()
    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill().frame(width: size, height: size).clipShape(Circle())
            } else {
                DearbyAvatar(name: name, filled: filled, size: size)
            }
        }
        .accessibilityHidden(true)
        .task(id: photoUrl) {
            guard let photoUrl else { image = nil; return }
            if let cached = Self.cache.object(forKey: photoUrl as NSString) { image = cached; return }
            image = nil
            guard let loader, let data = try? await loader.load(photoUrl), let loaded = UIImage(data: data) else { return }
            Self.cache.setObject(loaded, forKey: photoUrl as NSString)
            image = loaded
        }
    }
}
