import ImageIO
import UIKit

/// Square profile-photo crop (contract "프로필 사진" 2026-10-26): the picked photo upright (EXIF orientation applied), the person's square
/// chosen on it, and the JPEG the app uploads — at most 1024 px a side. HEIC decodes here, so the server only ever gets JPEG.
enum PhotoCrop {
    static let maxSide: CGFloat = 1024
    /// Decodes and shrinks to at most `maxPixel` a side with the orientation applied; a 48 MP photo would not fit the crop screen's memory.
    static func upright(_ data: Data, maxPixel: Int = 3072) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let options: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceCreateThumbnailWithTransform: true,
                                        kCGImageSourceThumbnailMaxPixelSize: maxPixel]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary).map { UIImage(cgImage: $0) }
    }
    /// The square on the image (pixels) under a circle of `diameter` points, when the image covers it at `zoom` (1 = shorter side fills
    /// the circle) and its centre sits `offset` points from the circle's centre.
    static func square(imageSize: CGSize, diameter: CGFloat, zoom: CGFloat, offset: CGSize) -> CGRect {
        let scale = diameter / min(imageSize.width, imageSize.height) * zoom
        let side = diameter / scale
        return CGRect(x: imageSize.width / 2 - (diameter / 2 + offset.width) / scale,
                      y: imageSize.height / 2 - (diameter / 2 + offset.height) / scale, width: side, height: side)
    }
    /// How far the image centre may move before an edge would show inside the circle.
    static func limit(imageSize: CGSize, diameter: CGFloat, zoom: CGFloat) -> CGSize {
        let scale = diameter / min(imageSize.width, imageSize.height) * zoom
        return CGSize(width: max(0, (imageSize.width * scale - diameter) / 2), height: max(0, (imageSize.height * scale - diameter) / 2))
    }
    /// The square as a JPEG of at most `maxSide` a side.
    static func jpeg(_ image: UIImage, square: CGRect) -> Data? {
        guard let cropped = image.cgImage?.cropping(to: square.integral) else { return nil }
        let side = min(maxSide, CGFloat(min(cropped.width, cropped.height)))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).jpegData(withCompressionQuality: 0.85) { _ in
            UIImage(cgImage: cropped).draw(in: CGRect(x: 0, y: 0, width: side, height: side))
        }
    }
}
