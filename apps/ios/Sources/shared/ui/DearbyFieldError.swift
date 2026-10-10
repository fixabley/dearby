import SwiftUI

/// 입력 칸 바로 아래에 붙는 오류 문구. 색만이 아니라 아이콘으로도 오류임을 보인다.
struct DearbyFieldError: View {
    let text: String
    var body: some View {
        Label(text, systemImage: "exclamationmark.circle").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.danger)
    }
}
