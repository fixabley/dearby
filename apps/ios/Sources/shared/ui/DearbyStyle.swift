import SwiftUI

/// 색 체계(contract "색 체계·다크 모드" 2026-10-19 밤): 중립색은 시스템 색에 맡기고 청록은 하나의 액센트로만 쓴다. 토큰 이름은 그대로 두고 시스템 색의 별칭으로 바꿨다.
/// 다크 모드는 시스템 설정을 따르며(라이트 강제 없음) 청록·오류색은 `UIColor { trait in … }`로 두 값을 가진다.
enum DearbyStyle {
    /// 브랜드 청록: 라이트 #007F80(로고 색), 다크 #3FB3B4. `SafariView`처럼 UIKit이 필요한 곳은 `tealUIColor`.
    static let tealUIColor = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 63.0 / 255, green: 179.0 / 255, blue: 180.0 / 255, alpha: 1)
            : UIColor(red: 0, green: 127.0 / 255, blue: 128.0 / 255, alpha: 1)
    }
    static let teal = Color(uiColor: tealUIColor)
    /// 청록 채움 위의 글자·아이콘·스피너: 라이트 흰색, 다크 #0C4848(Android `onPrimary`와 같은 값) — 다크의 밝은 청록 위에서 읽히도록.
    static let onTeal = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark ? UIColor(red: 12.0 / 255, green: 72.0 / 255, blue: 72.0 / 255, alpha: 1) : .white
    })
    /// 보조 글자 → `.secondary`.
    static let quiet = Color.secondary
    /// 연청록 바탕(글자는 청록) → 청록 12%, 다크에서도 따라온다.
    static let mint = teal.opacity(0.12)
    /// 구분선·테두리 → `separator`.
    static let line = Color(.separator)
    /// 비활성 칩·입력 면 채움 → `tertiarySystemFill`.
    static let muted = Color(.tertiarySystemFill)
    /// 본문 글자 → `.primary`.
    static let ink = Color.primary
    /// 오류 글자·테두리: 라이트는 웹 `--danger`와 같은 #A12A2A, 다크는 검은 바탕에서 읽히는 밝은 빨강(#F0625C).
    static let danger = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 240.0 / 255, green: 98.0 / 255, blue: 92.0 / 255, alpha: 1)
            : UIColor(red: 161.0 / 255, green: 42.0 / 255, blue: 42.0 / 255, alpha: 1)
    })
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

/// 브랜드 표시: 심볼(맞물린 두 고리, 2026-10-19 사용자 선택) 아래 앱 이름. 소개 화면과 로그인 시트에만 쓰고, 일반 화면에는 로고를 두지 않는다(2026-10-19 사용자 결정).
/// 심볼은 청록 단색이라 템플릿으로 그려 다크 모드에서 밝은 청록을 따른다.
struct DearbyBrand: View {
    var symbolSize: CGFloat = 72
    var body: some View {
        VStack(spacing: 12) {
            Image("DearbySymbol").renderingMode(.template).resizable().scaledToFit().frame(width: symbolSize, height: symbolSize)
                .foregroundStyle(DearbyStyle.teal)
            Text("Dearby").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.teal)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore).accessibilityLabel("Dearby")
    }
}
