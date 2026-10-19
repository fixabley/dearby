import SwiftUI

/// 사용자가 골라 추가한 연락처 한 줄의 값.
struct ProfileExtraContact: Identifiable, Equatable {
    let id: String
    let kind: String
    var value: String
}

/// 프로필 연락처 섹션(폼 `Section`). 전화번호·이메일은 필수 행이고, 그 밖의 종류는 `연락처 추가` 행에서 골라 붙이고 왼쪽으로 밀어 지운다.
/// 값·형식 검사·오류 문구는 화면이 갖고 이 위젯은 행으로 보이며 오류는 footer에 둔다.
struct ProfileContactFields: View {
    @Binding var phone: String
    @Binding var email: String
    @Binding var extras: [ProfileExtraContact]
    var phoneError: String?
    var emailError: String?
    /// 시트에서 고른 종류. 화면이 새 항목을 `extras`에 넣는다.
    let add: (String) -> Void
    @State private var choosing = false
    static let kinds: [(kind: String, label: String, prompt: String)] = [
        ("kakao", "카카오톡", "오픈채팅 링크 또는 ID"), ("instagram", "Instagram", "@아이디"),
        ("github", "GitHub", "github.com/아이디"), ("behance", "Behance", "behance.net/아이디")
    ]
    var body: some View {
        Section {
            LabeledContent("전화번호") {
                TextField("010-0000-0000", text: $phone).keyboardType(.phonePad).textContentType(.telephoneNumber)
                    .foregroundStyle(.primary).accessibilityLabel("전화번호, 필수")
            }
            LabeledContent("이메일") {
                TextField("name@example.com", text: $email).keyboardType(.emailAddress).textContentType(.emailAddress)
                    .textInputAutocapitalization(.never).autocorrectionDisabled().foregroundStyle(.primary).accessibilityLabel("이메일, 필수")
            }
            ForEach($extras) { $item in
                let meta = Self.kinds.first { $0.kind == item.kind }
                let label = meta?.label ?? item.kind
                LabeledContent(label) {
                    TextField(meta?.prompt ?? "", text: $item.value)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().foregroundStyle(.primary).accessibilityLabel(label)
                }
                .swipeActions {
                    Button("삭제", role: .destructive) { extras.removeAll { $0.id == item.id } }.accessibilityLabel("\(label) 삭제")
                }
            }
            Button { choosing = true } label: { Label("연락처 추가", systemImage: "plus") }
                .sheet(isPresented: $choosing) {
                    NavigationStack {
                        List(Self.kinds, id: \.kind) { option in
                            Button { choosing = false; add(option.kind) } label: {
                                Text(option.label).font(.dearby(.body)).foregroundStyle(DearbyStyle.ink).frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        .navigationTitle("추가할 연락처").navigationBarTitleDisplayMode(.inline)
                        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { choosing = false } } }
                    }.presentationDetents([.medium])
                }
        } header: {
            Text("연락처").font(.dearby(.footnote))
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                Text("전화번호와 이메일은 꼭 적어 주세요. 추가한 연락처는 왼쪽으로 밀어 지워요.")
                if let phoneError { DearbyFieldError(text: phoneError) }
                if let emailError { DearbyFieldError(text: emailError) }
            }.font(.dearby(.footnote))
        }
    }
}

/// 활동 이력의 기간 입력 행들(폼 섹션 안): 고른 기간 `2026.03 – 2026.06`, 시작일(필수)·종료일 `DatePicker`와 `진행 중` 스위치.
/// 날짜 검사(종료일이 시작일보다 앞서지 않음)와 오류 문구는 화면이 footer에 둔다.
struct HistoryPeriodFields: View {
    @Binding var start: Date?
    @Binding var end: Date?
    @Binding var ongoing: Bool
    var body: some View {
        LabeledContent("기간", value: Self.period(start: start, end: ongoing ? nil : end, ongoing: ongoing))
        dateRow("시작일", required: true, date: $start, earliest: nil)
        Toggle("진행 중", isOn: $ongoing)
        if !ongoing { dateRow("종료일", required: false, date: $end, earliest: start) }
    }
    /// `earliest`가 있으면(종료일) 그보다 앞선 날을 고를 수 없다. 날짜가 없으면 `날짜 선택` 버튼이 오늘(또는 `earliest`)로 채운다.
    @ViewBuilder private func dateRow(_ label: String, required: Bool, date: Binding<Date?>, earliest: Date?) -> some View {
        if let value = date.wrappedValue {
            DatePicker(label, selection: Binding(get: { value }, set: { date.wrappedValue = $0 }),
                       in: (earliest ?? .distantPast)..., displayedComponents: .date)
                .environment(\.locale, Locale(identifier: "ko_KR"))
                .accessibilityLabel(required ? "\(label), 필수" : label)
        } else {
            LabeledContent(label) {
                Button("날짜 선택") { date.wrappedValue = max(.now, earliest ?? .distantPast) }.buttonStyle(.borderless).foregroundStyle(DearbyStyle.teal)
                    .accessibilityLabel("\(label) 선택\(required ? ", 필수" : "")")
            }
        }
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
    var body: some View {
        Form {
            ProfileContactFields(phone: $phone, email: $email, extras: $extras,
                                 phoneError: "전화번호는 숫자 8~15자리로 입력해 주세요.") { kind in
                extras.append(ProfileExtraContact(id: UUID().uuidString, kind: kind, value: ""))
            }
            Section("기간") { HistoryPeriodFields(start: $start, end: $end, ongoing: $ongoing) }
        }.font(.dearby(.body))
    }
}

#Preview("프로필 입력") { ProfileFieldsSample() }
#endif
