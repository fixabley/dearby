import CoreImage
import CoreImage.CIFilterBuiltins
import SwiftUI

/// 문자열을 QR 이미지로 그린다. 확대해도 흐려지지 않게 픽셀을 보간하지 않는다. 접근성 이름은 쓰는 쪽이 붙인다.
struct DearbyQRCode: View {
    let text: String
    var body: some View {
        if let image = Self.render(text) {
            Image(decorative: image, scale: 1).interpolation(.none).resizable().scaledToFit()
        } else {
            Color.clear
        }
    }
    private static func render(_ text: String) -> CGImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        return CIContext().createCGImage(output, from: output.extent)
    }
}
