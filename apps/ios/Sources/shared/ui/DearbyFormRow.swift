import SwiftUI

/// 폼 입력 행(contract "플로팅 라벨·필수 표시"·"입력 영역 터치" 2026-10-20): 칸은 발견 검색창과 같은 둥근 10 `tertiarySystemFill` 상자(한 줄 44, 여러 줄은 3줄 고정, 테두리 없음)이고
/// 포커스·값에 따라 모양이 바뀌지 않는다. 라벨 하나가 비어 있고 포커스 없을 때는 칸 안 placeholder 자리(`.body` 보조색)에, 포커스가 가거나 값이 있으면 칸 **위 왼쪽**(바깥)으로
/// `scaleEffect`+`offset`(`.snappy(0.2)`)으로 작아져 올라간다(`.subheadline` 크기, 보조색·포커스 시 청록). 라벨 줄 20 + 간격 4는 처음부터 확보해 레이아웃이 튀지 않는다.
/// `required`면 라벨 끝에 빨간 `*`, 접근성 라벨은 "<라벨>, 필수". `symbol`은 연락처 행의 왼쪽 24 보조색 글리프(라벨은 그대로 플로팅). 행 어디를 눌러도 `focus`가 `value`가 된다.
/// `verified`면 값 끝(칸 안 오른쪽)에 `DearbyVerifiedMark`를 두고 접근성 라벨에 `인증됨`을 더한다(계약 "연락처 인증" 2026-10-26).
/// `TextField`는 행이 만들고(`text`, `multiline`), 키보드·자동 완성·제출 같은 수식어는 `configure`로 받는다.
struct DearbyFormRow<Value: Hashable, Field: View>: View {
    let label: String
    var symbol: DearbyGlyph?
    /// Fixed text before the value (a username field's `github.com/`), shown while the label is floated.
    var prefix: String?
    var required = false
    @Binding var text: String
    var multiline = false
    var focus: FocusState<Value?>.Binding
    let value: Value
    var verified = false
    let configure: (TextField<Text>) -> Field
    init(label: String, symbol: DearbyGlyph? = nil, prefix: String? = nil, required: Bool = false, text: Binding<String>, multiline: Bool = false,
         focus: FocusState<Value?>.Binding, value: Value, verified: Bool = false, configure: @escaping (TextField<Text>) -> Field) {
        self.label = label
        self.symbol = symbol
        self.prefix = prefix
        self.required = required
        _text = text
        self.multiline = multiline
        self.focus = focus
        self.value = value
        self.verified = verified
        self.configure = configure
    }
    private var focused: Bool { focus.wrappedValue == value }
    private var floated: Bool { focused || !text.isEmpty }
    /// Box left edge: after the 24 glyph + 12 gap when there is a symbol.
    private var inset: CGFloat { symbol == nil ? 0 : 36 }
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Color.clear.frame(height: 20)
            HStack(spacing: 12) {
                if let symbol { DearbyGlyphView(glyph: symbol) }
                box
            }
        }
        .overlay(alignment: .topLeading) { floatingLabel }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture { focus.wrappedValue = value }
    }
    private var box: some View {
        HStack(spacing: 0) {
            if let prefix, floated { Text(prefix).font(.dearby(.body)).foregroundStyle(.secondary).accessibilityHidden(true) }
            configure(TextField("", text: $text, axis: multiline ? .vertical : .horizontal))
                .lineLimit(multiline ? 3 : 1, reservesSpace: multiline)
                .font(.dearby(.body)).foregroundStyle(.primary)
                .focused(focus, equals: value)
                .accessibilityLabel((required ? "\(label), 필수" : label) + (verified ? ", 인증됨" : ""))
            if verified { DearbyVerifiedMark().accessibilityHidden(true).padding(.leading, 8) }
        }
            .padding(.horizontal, 12).padding(.vertical, 11).frame(minHeight: 44)
            .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
    /// One label drawn at `.body`; floated it scales to `.subheadline` (15/17) at the row's top-left, otherwise it sits where the first text line starts
    /// (label line 20 + gap 4 + box top padding 11). Hidden from VoiceOver (the field carries the name) and from taps (the field underneath gets them).
    private var floatingLabel: some View {
        (Text(label) + (required ? Text(" *").foregroundStyle(.red) : Text("")))
            .font(.dearby(.body)).foregroundStyle(focused ? DearbyStyle.teal : .secondary).lineLimit(1)
            .scaleEffect(floated ? 15 / 17 : 1, anchor: .topLeading)
            .offset(x: floated ? inset : inset + 12, y: floated ? 0 : 35)
            .animation(.snappy(duration: 0.2), value: floated)
            .allowsHitTesting(false).accessibilityHidden(true)
    }
}

/// Row-leading glyph in a 24 column, secondary color: an SF Symbol, or a template image asset (a brand mark, drawn 20 so it sits like the symbols).
enum DearbyGlyph: Equatable { case system(String), asset(String) }

struct DearbyGlyphView: View {
    let glyph: DearbyGlyph
    @ScaledMetric(relativeTo: .title3) private var assetSide: CGFloat = 20
    var body: some View {
        Group {
            switch glyph {
            case .system(let name): Image(systemName: name).font(.dearby(.title3))
            case .asset(let name): Image(name).renderingMode(.template).resizable().scaledToFit().frame(width: assetSide, height: assetSide)
            }
        }.foregroundStyle(.secondary).frame(width: 24).accessibilityHidden(true)
    }
}

extension DearbyFormRow where Field == TextField<Text> {
    init(label: String, symbol: DearbyGlyph? = nil, prefix: String? = nil, required: Bool = false, text: Binding<String>, multiline: Bool = false,
         focus: FocusState<Value?>.Binding, value: Value, verified: Bool = false) {
        self.init(label: label, symbol: symbol, prefix: prefix, required: required, text: text, multiline: multiline, focus: focus, value: value, verified: verified) { $0 }
    }
}

/// 읽기 전용 행(고른 기간, 접힌 퍼널 단계처럼 `LabeledContent`의 가로 라벨·값을 대신한다): `DearbyFormRow`가 떠 있을 때와 같은 모양 —
/// 라벨 줄 20(`.subheadline` 보조색) + 간격 4 + 둥근 10 `tertiarySystemFill` 상자(최소 44) 안의 `.body` 값. 값이 비면 `empty`를 보조색으로 보인다.
struct DearbyValueRow: View {
    let label: String
    let value: String
    var empty = "비워 둠"
    var body: some View {
        DearbyFieldBox(label: label) {
            Text(value.isEmpty ? empty : value).font(.dearby(.body)).foregroundStyle(value.isEmpty ? .secondary : .primary)
                .multilineTextAlignment(.leading).padding(.vertical, 11)
        }
    }
}

/// `DearbyValueRow`·날짜 행이 함께 쓰는 틀: 칸 위 라벨 줄(20) + 간격 4 + 둥근 10 `tertiarySystemFill` 상자(가로 여백 12, 최소 44). 상자 안 내용은 왼쪽 정렬.
struct DearbyFieldBox<Content: View>: View {
    let label: String
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.dearby(.subheadline)).foregroundStyle(.secondary).lineLimit(1).frame(minHeight: 20)
            content.padding(.horizontal, 12).frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// 체크박스 행(contract "토글 라벨은 바로 옆" 2026-10-20 — 여러 줄 라벨처럼 스위치를 바로 옆에 두면 어색한 곳): 행 전체가 `Button`이고 오른쪽에
/// `checkmark.circle`(켜지면 `.fill` 청록). `.disabled`면 글자·표시가 보조색으로 흐려진다. 접근성은 토글로 읽힌다.
struct DearbyCheckRow<Label: View>: View {
    @Binding var isOn: Bool
    @ViewBuilder let label: Label
    @Environment(\.isEnabled) private var isEnabled
    var body: some View {
        Button { isOn.toggle() } label: {
            HStack(spacing: 12) {
                label.frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: isOn ? "checkmark.circle.fill" : "checkmark.circle").font(.dearby(.title2))
                    .foregroundStyle(isOn && isEnabled ? DearbyStyle.teal : .secondary).accessibilityHidden(true)
            }
            .frame(minHeight: 44).contentShape(Rectangle())
        }
        .foregroundStyle(isEnabled ? DearbyStyle.ink : .secondary)
        .accessibilityAddTraits(.isToggle).accessibilityValue(isOn ? "켬" : "끔")
    }
}

#if DEBUG
private struct DearbyFormRowGallery: View {
    @State var name = ""
    @State var phone = ""
    @State var note = "여러 줄 메모"
    @State var agreed = false
    @FocusState var focus: String?
    var body: some View {
        Form {
            Section("기본 정보") {
                DearbyFormRow(label: "이름", required: true, text: $name, focus: $focus, value: "name") { $0.textContentType(.name) }
                DearbyFormRow(label: "소개", text: $note, multiline: true, focus: $focus, value: "note")
                DearbyFormRow(label: "전화번호", symbol: .system("phone"), required: true, text: $phone, focus: $focus, value: "phone") { $0.keyboardType(.phonePad) }
                DearbyValueRow(label: "직무", value: "")
                DearbyCheckRow(isOn: $agreed) { Text("개인정보 수집·이용에 동의합니다. 여러 줄이 되는 라벨도 행 전체가 눌려요.") }
            }
        }.font(.dearby(.body)).dearbyDismissKeyboard { focus = nil }
    }
}

#Preview("폼 행") { DearbyFormRowGallery() }
#endif
