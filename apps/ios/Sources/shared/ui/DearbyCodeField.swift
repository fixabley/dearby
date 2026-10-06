import SwiftUI

/// 인증번호 입력 칸. 숫자 칸을 나눠 보이지만 실제 입력은 위에 겹친 투명한 TextField 하나가 받아,
/// 붙여넣기·OS 인증번호 자동 입력·숫자 키패드와 UI 테스트의 `typeText`가 그대로 동작한다.
struct DearbyCodeField: View {
    @Binding var code: String
    var length = 6
    let label: String
    var isError = false
    var identifier = "code"
    @FocusState private var focused: Bool
    var body: some View {
        ZStack {
            HStack(spacing: 8) {
                ForEach(0..<length, id: \.self) { index in
                    let digits = Array(code)
                    let current = focused && index == min(digits.count, length - 1)
                    Text(index < digits.count ? String(digits[index]) : "")
                        .font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.ink)
                        .frame(maxWidth: .infinity, minHeight: 56)
                        .background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10)
                            .stroke(isError ? DearbyStyle.danger : (current ? DearbyStyle.teal : DearbyStyle.line), lineWidth: current || isError ? 2 : 1))
                }
            }.accessibilityHidden(true)
            TextField("", text: Binding(get: { code }, set: { code = String($0.filter(\.isNumber).prefix(length)) }))
                .keyboardType(.numberPad).textContentType(.oneTimeCode)
                .foregroundStyle(.clear).tint(.clear).focused($focused)
                .frame(maxWidth: .infinity, minHeight: 56)
                .accessibilityLabel(label).accessibilityValue(code.isEmpty ? "비어 있음" : code.map(String.init).joined(separator: " "))
                .accessibilityHint("\(length)자리 숫자").accessibilityIdentifier(identifier)
        }
    }
}

/// 입력 칸 바로 아래에 붙는 오류 문구. 색만이 아니라 아이콘으로도 오류임을 보인다.
struct DearbyFieldError: View {
    let text: String
    var body: some View {
        Label(text, systemImage: "exclamationmark.circle").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.danger)
    }
}
