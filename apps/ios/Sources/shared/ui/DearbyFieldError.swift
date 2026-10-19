import SwiftUI

/// 입력 행 아래(폼 섹션의 footer)에 붙는 오류 문구: `.footnote` 빨강(contract "폼은 플랫폼 기본 컴포넌트"). 색만이 아니라 아이콘으로도 오류임을 보인다.
struct DearbyFieldError: View {
    let text: String
    var body: some View {
        Label(text, systemImage: "exclamationmark.triangle").font(.dearby(.footnote)).foregroundStyle(DearbyStyle.danger)
    }
}
