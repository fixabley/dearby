import SwiftUI

enum DearbyStyle {
    static let teal = Color(red: 0, green: 127.0 / 255, blue: 128.0 / 255)
    static let quiet = Color(red: 101.0 / 255, green: 112.0 / 255, blue: 120.0 / 255)
    static let mint = Color(red: 240.0 / 255, green: 252.0 / 255, blue: 250.0 / 255)
    static let line = Color(red: 227.0 / 255, green: 232.0 / 255, blue: 234.0 / 255)
    static let muted = Color(red: 245.0 / 255, green: 247.0 / 255, blue: 248.0 / 255)
    static let ink = Color(red: 23.0 / 255, green: 32.0 / 255, blue: 39.0 / 255)
    /// 오류 글자·테두리. 웹 `--danger`와 같은 값(#A12A2A).
    static let danger = Color(red: 161.0 / 255, green: 42.0 / 255, blue: 42.0 / 255)
}

extension Font {
    /// Pretendard로 기존 글자 스타일의 크기·굵기를 그대로 쓰고 시스템 글자 크기 설정을 따른다. 원본: 저장소 루트 shared/assets/fonts/pretendard.
    static func dearby(_ style: Font.TextStyle) -> Font {
        let (size, weight): (CGFloat, Font.Weight) = switch style {
        case .largeTitle: (34, .regular)
        case .title: (28, .regular)
        case .title2: (22, .regular)
        case .title3: (20, .regular)
        case .headline: (17, .semibold)
        case .callout: (16, .regular)
        case .subheadline: (15, .regular)
        case .footnote: (13, .regular)
        case .caption: (12, .regular)
        case .caption2: (11, .regular)
        default: (17, .regular)
        }
        return .custom("Pretendard", size: size, relativeTo: style).weight(weight)
    }
    /// 글자 크기 설정과 무관하게 고정 크기를 쓰던 곳(`.system(size:)`)의 Pretendard 대응.
    static func dearby(fixedSize size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .custom("Pretendard", fixedSize: size).weight(weight)
    }
}

struct DearbyLogo: View {
    var width: CGFloat = 92
    var body: some View {
        Image("DearbyLogo").resizable().scaledToFit().frame(width: width).accessibilityLabel("Dearby")
    }
}

struct DearbyButtonStyle: ButtonStyle {
    var outlined = false
    @Environment(\.isEnabled) private var isEnabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.dearby(.headline)).frame(maxWidth: .infinity, minHeight: 50)
            .foregroundStyle(outlined ? DearbyStyle.teal : .white)
            .background(outlined ? .white : DearbyStyle.teal, in: RoundedRectangle(cornerRadius: 11))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(DearbyStyle.teal, lineWidth: outlined ? 1 : 0))
            .opacity(!isEnabled ? 0.45 : (configuration.isPressed ? 0.7 : 1))
    }
}
