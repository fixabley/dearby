import SwiftUI

/// 사용자가 골라 추가한 연락처 한 줄의 값.
struct ProfileExtraContact: Identifiable, Equatable {
    let id: String
    let kind: String
    var value: String
}

/// 프로필 연락처 섹션(폼 `Section`). 전화번호·이메일은 필수 행이고, 그 밖의 종류는 `연락처 추가` 행에서 골라 붙이고 왼쪽으로 밀어 지운다.
/// 행은 왼쪽에 종류 아이콘(`ContactModel.symbol(kind:)`)을 둔 플로팅 라벨 `DearbyFormRow`이고 종류 이름이 라벨(비면 placeholder 자리)이다(계약 "연락처 행은 라벨 대신 왼쪽 아이콘"·"플로팅 라벨" 2026-10-20);
/// 행 어디를 눌러도 그 필드에 포커스가 간다(`focus`의 값은 `phone`·`email`·추가 연락처의 id).
/// 값·형식 검사·오류 문구는 화면이 갖고 이 위젯은 행으로 보이며 오류는 footer에 둔다.
struct ProfileContactFields: View {
    @Binding var phone: String
    @Binding var email: String
    @Binding var extras: [ProfileExtraContact]
    var focus: FocusState<String?>.Binding
    var phoneError: String?
    var emailError: String?
    /// 시트에서 고른 종류. 화면이 새 항목을 `extras`에 넣는다.
    let add: (String) -> Void
    @State private var choosing = false
    static let kinds: [(kind: String, label: String)] = [("kakao", "카카오톡"), ("instagram", "Instagram"), ("github", "GitHub"), ("behance", "Behance")]
    var body: some View {
        Section {
            DearbyFormRow(label: "전화번호", symbol: ContactModel.symbol(kind: "phone"), required: true, text: $phone, focus: focus, value: "phone") {
                $0.keyboardType(.phonePad).textContentType(.telephoneNumber)
            }
            DearbyFormRow(label: "이메일", symbol: ContactModel.symbol(kind: "email"), required: true, text: $email, focus: focus, value: "email") {
                $0.keyboardType(.emailAddress).textContentType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
            ForEach($extras) { $item in
                let label = Self.kinds.first { $0.kind == item.kind }?.label ?? item.kind
                DearbyFormRow(label: label, symbol: ContactModel.symbol(kind: item.kind), text: $item.value, focus: focus, value: item.id) {
                    $0.textInputAutocapitalization(.never).autocorrectionDisabled()
                }
                .swipeActions {
                    Button("삭제", role: .destructive) { extras.removeAll { $0.id == item.id } }.accessibilityLabel("\(label) 삭제")
                }
            }
            Button { choosing = true } label: {
                Label("연락처 추가", systemImage: "plus").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
            }
            .sheet(isPresented: $choosing) {
                NavigationStack {
                    List(Self.kinds, id: \.kind) { option in
                        Button { choosing = false; add(option.kind) } label: {
                            Label { Text(option.label).foregroundStyle(DearbyStyle.ink) } icon: {
                                Image(systemName: ContactModel.symbol(kind: option.kind)).foregroundStyle(.secondary)
                            }.font(.dearby(.body)).frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                        }
                    }
                    .navigationTitle("추가할 연락처").navigationBarTitleDisplayMode(.inline)
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { choosing = false } } }
                }.presentationDetents([.medium])
            }
        } header: {
            Text("연락처").font(.dearby(.subheadline))
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                Text("추가한 연락처는 왼쪽으로 밀어 지워요.")
                if let phoneError { DearbyFieldError(text: phoneError) }
                if let emailError { DearbyFieldError(text: emailError) }
            }.font(.dearby(.subheadline))
        }
    }
}

/// 활동 이력의 기간 입력 행들(폼 섹션 안): 고른 기간 `2026.03 – 2026.06`(라벨 위·값 아래), 시작일(필수)·종료일은 라벨 아래 시스템 `DatePicker`(compact)와
/// `진행 중` 스위치(라벨 바로 옆, 행 전체 터치). 날짜 검사(종료일이 시작일보다 앞서지 않음)와 오류 문구는 화면이 footer에 둔다.
struct HistoryPeriodFields: View {
    @Binding var start: Date?
    @Binding var end: Date?
    @Binding var ongoing: Bool
    var body: some View {
        DearbyValueRow(label: "기간", value: Self.period(start: start, end: ongoing ? nil : end, ongoing: ongoing))
        dateRow("시작일", required: true, date: $start, earliest: nil)
        Toggle("진행 중", isOn: $ongoing)
        if !ongoing { dateRow("종료일", required: false, date: $end, earliest: start) }
    }
    /// `earliest`가 있으면(종료일) 그보다 앞선 날을 고를 수 없다. 날짜가 없으면 행 전체가 `날짜 선택` 버튼이고 오늘(또는 `earliest`)로 채운다.
    private func dateRow(_ label: String, required: Bool, date: Binding<Date?>, earliest: Date?) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.dearby(.subheadline).weight(.medium)).foregroundStyle(.secondary)
            if let value = date.wrappedValue {
                DatePicker(label, selection: Binding(get: { value }, set: { date.wrappedValue = $0 }),
                           in: (earliest ?? .distantPast)..., displayedComponents: .date)
                    .labelsHidden().environment(\.locale, Locale(identifier: "ko_KR"))
                    .accessibilityLabel(required ? "\(label), 필수" : label)
            } else {
                Button { date.wrappedValue = max(.now, earliest ?? .distantPast) } label: {
                    Text("날짜 선택").font(.dearby(.title3)).foregroundStyle(DearbyStyle.teal).frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                }
                .accessibilityLabel("\(label) 선택\(required ? ", 필수" : "")")
            }
        }
        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
    }
    /// 연·월 위주 표시: `2026.03 – 2026.06`, 진행 중이면 `2026.03 – 진행 중`.
    static func period(start: Date?, end: Date?, ongoing: Bool) -> String {
        guard let start else { return "시작일을 골라 주세요" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy.MM"
        let tail = ongoing ? "진행 중" : end.map { formatter.string(from: $0) } ?? "종료일 미정"
        return "\(formatter.string(from: start)) – \(tail)"
    }
}

#if DEBUG
/// 미리보기와 캡처 테스트가 함께 쓰는 예시. 연락처는 실제가 아닌 example.invalid 값이다.
struct ProfileFieldsSample: View {
    @State var phone = "010-12"
    @State var email = "jimin@example.invalid"
    @State var extras = [ProfileExtraContact(id: "x1", kind: "instagram", value: "@jimin")]
    @State var start: Date? = ISO8601DateFormatter().date(from: "2026-03-01T00:00:00Z")
    @State var end: Date? = ISO8601DateFormatter().date(from: "2026-06-01T00:00:00Z")
    @State var ongoing = false
    @FocusState var focus: String?
    var body: some View {
        Form {
            ProfileContactFields(phone: $phone, email: $email, extras: $extras, focus: $focus,
                                 phoneError: "전화번호는 숫자 8~15자리로 입력해 주세요.") { kind in
                extras.append(ProfileExtraContact(id: UUID().uuidString, kind: kind, value: ""))
            }
            Section("기간") { HistoryPeriodFields(start: $start, end: $end, ongoing: $ongoing) }
        }.font(.dearby(.body)).dearbyDismissKeyboard { focus = nil }
    }
}

#Preview("프로필 입력") { ProfileFieldsSample() }
#endif
