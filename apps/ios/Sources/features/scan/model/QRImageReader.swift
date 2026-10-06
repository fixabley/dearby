import CoreImage
import Foundation

/// Reads a QR code from a photo the user picked with the system photo picker (no photo library permission).
enum QRImageReader {
    static func text(in data: Data) -> String? {
        guard let image = CIImage(data: data),
              let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: nil, options: [CIDetectorAccuracy: CIDetectorAccuracyHigh])
        else { return nil }
        return detector.features(in: image).compactMap { ($0 as? CIQRCodeFeature)?.messageString }.first
    }
}
