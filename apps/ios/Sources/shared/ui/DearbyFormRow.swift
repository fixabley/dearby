import SwiftUI

/// 폼 입력 행(contract "폼 라벨·입력 크기"·"입력 영역 터치" 2026-10-20, 토스 입력 화면 참고): 라벨 `.subheadline` 중간 굵기 보조색을 입력 위에 쌓고
/// 입력은 `.title3` 본문색(placeholder는 같은 크기 보조색, 시스템 기본), 행 최소 높이 64(내용 44 + 폼 행 기본 세로 inset). 행 어디를 눌러도 `focus`가 `value`가 되어
/// 그 필드로 키보드가 열린다. `content`는 `TextField` 하나(여러 줄은 `axis: .vertical` + `lineLimit(3...)`)이며 `.focused`는 행이 붙인다.
struct DearbyFormRow<Value: Hashable, Content: View>: View {
    let label: String
    var focus: FocusState<Value?>.Binding
    let value: Value
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.dearby(.subheadline).weight(.medium)).foregroundStyle(.secondary)
            content.font(.dearby(.title3)).foregroundStyle(.primary).focused(focus, equals: value)
        }
        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture { focus.wrappedValue = value }
    }
}

/// 라벨 위·값 아래로 쌓인 읽기 전용 행(고른 기간, 접힌 퍼널 단계처럼 `LabeledContent`의 가로 라벨·값을 대신한다). 값이 비면 `empty`를 보조색으로 보인다.
struct DearbyValueRow: View {
    let label: String
    let value: String
    var empty = "비워 둠"
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.dearby(.subheadline).weight(.medium)).foregroundStyle(.secondary)
            Text(value.isEmpty ? empty : value).font(.dearby(.title3)).foregroundStyle(value.isEmpty ? .secondary : .primary)
                .multilineTextAlignment(.leading)
        }
        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
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
    @State var note = "여러 줄 메모"
    @State var agreed = false
    @FocusState var focus: String?
    var body: some View {
        Form {
            Section("기본 정보") {
                DearbyFormRow(label: "이름", focus: $focus, value: "name") { TextField("실명 또는 활동명", text: $name) }
                DearbyFormRow(label: "소개", focus: $focus, value: "note") { TextField("한두 문장", text: $note, axis: .vertical).lineLimit(3...6) }
                DearbyValueRow(label: "직무", value: "")
                DearbyCheckRow(isOn: $agreed) { Text("개인정보 수집·이용에 동의합니다. 여러 줄이 되는 라벨도 행 전체가 눌려요.") }
            }
        }.font(.dearby(.body)).dearbyDismissKeyboard { focus = nil }
    }
}

#Preview("폼 행") { DearbyFormRowGallery() }
#endif
