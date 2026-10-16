import SwiftUI

/// The activity's image (`imageUrl`, https only) or a calendar tile when there is none or it fails to load.
struct ActivityArtwork: View {
    let imageURL: URL?
    var body: some View {
        AsyncImage(url: imageURL) { phase in
            if let image = phase.image {
                image.resizable().scaledToFill().accessibilityLabel("활동 이미지")
            } else {
                ZStack {
                    DearbyStyle.mint
                    Image(systemName: "calendar").font(.dearby(.largeTitle)).foregroundStyle(DearbyStyle.teal)
                }.accessibilityHidden(true)
            }
        }
    }
}
