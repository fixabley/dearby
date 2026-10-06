import SwiftUI

/// 사용자가 골라 추가한 연락처 한 줄의 값.
struct ProfileExtraContact: Identifiable, Equatable {
    let id: String
    let kind: String
    var value: String
}

/// 프로필 연락처 입력. 전화번호·이메일은 필수 칸이고, 그 밖의 종류는 `연락처 추가`에서 골라 붙이고 지울 수 있다.
/// 값·형식 검사·오류 문구는 화면이 갖고 이 위젯은 보이고 바뀐 값을 알린다.
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
        VStack(alignment: .leading, spacing: 14) {
            required("전화번호", text: $phone, prompt: "010-0000-0000", error: phoneError)
                .keyboardType(.phonePad).textContentType(.telephoneNumber)
            required("이메일", text: $email, prompt: "name@example.com", error: emailError)
                .keyboardType(.emailAddress).textContentType(.emailAddress)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
            ForEach($extras) { $item in
                let meta = Self.kinds.first { $0.kind == item.kind }
                HStack(alignment: .bottom, spacing: 8) {
                    labeled(meta?.label ?? item.kind, required: false) {
                        DearbyInlineField(label: meta?.label ?? item.kind, text: $item.value, editing: true, prompt: meta?.prompt ?? "")
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                    }
                    Button { extras.removeAll { $0.id == item.id } } label: {
                        Image(systemName: "minus.circle").font(.dearby(.title3)).foregroundStyle(DearbyStyle.quiet).frame(width: 44, height: 44)
                    }.accessibilityLabel("\(meta?.label ?? item.kind) 삭제")
                }
            }
            Button { choosing = true } label: {
                Label("연락처 추가", systemImage: "plus").font(.dearby(.subheadline).weight(.semibold))
                    .foregroundStyle(DearbyStyle.teal).frame(maxWidth: .infinity, minHeight: 44)
                    .overlay(RoundedRectangle(cornerRadius: 11).stroke(DearbyStyle.line))
            }.buttonStyle(.plain)
        }
        .sheet(isPresented: $choosing) {
            NavigationStack {
                List(Self.kinds, id: \.kind) { option in
                    Button { choosing = false; add(option.kind) } label: {
                        Text(option.label).font(.dearby(.body)).foregroundStyle(DearbyStyle.ink).frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    }
                }
                .navigationTitle("추가할 연락처").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { choosing = false } } }
            }.presentationDetents([.medium])
        }
    }
    private func required(_ label: String, text: Binding<String>, prompt: String, error: String?) -> some View {
        labeled(label, required: true) {
            DearbyInlineField(label: "\(label), 필수", text: text, editing: true, prompt: prompt)
            if let error { DearbyFieldError(text: error) }
        }
    }
    private func labeled(_ label: String, required: Bool, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                Text(label).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                if required { Text("필수").font(.dearby(.caption2).weight(.bold)).foregroundStyle(DearbyStyle.danger) }
            }.accessibilityHidden(true)
            content()
        }
    }
}

/// 활동 이력의 기간 입력. 시작일(필수)·종료일 선택기와 `진행 중` 스위치를 두고, 고른 기간을 `2026.03 – 2026.06`으로 보인다.
/// 날짜 검사(종료일이 시작일보다 앞서지 않음)와 오류 문구는 화면이 맡는다.
struct HistoryPeriodFields: View {
    @Binding var start: Date?
    @Binding var end: Date?
    @Binding var ongoing: Bool
    var error: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("기간").font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                Spacer()
                Text(Self.period(start: start, end: ongoing ? nil : end, ongoing: ongoing))
                    .font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(start == nil ? DearbyStyle.quiet : DearbyStyle.ink)
            }.accessibilityElement(children: .combine)
            dateRow("시작일", required: true, date: $start, earliest: nil)
            Toggle("진행 중", isOn: $ongoing).font(.dearby(.subheadline)).tint(DearbyStyle.teal).frame(minHeight: 44)
            if !ongoing { dateRow("종료일", required: false, date: $end, earliest: start) }
            if let error { DearbyFieldError(text: error) }
        }
    }
    /// `earliest`가 있으면(종료일) 그보다 앞선 날을 고를 수 없다.
    @ViewBuilder private func dateRow(_ label: String, required: Bool, date: Binding<Date?>, earliest: Date?) -> some View {
        HStack {
            Text(label).font(.dearby(.subheadline))
            if required { Text("필수").font(.dearby(.caption2).weight(.bold)).foregroundStyle(DearbyStyle.danger) }
            Spacer()
            if let value = date.wrappedValue {
                DatePicker(label, selection: Binding(get: { value }, set: { date.wrappedValue = $0 }),
                           in: (earliest ?? .distantPast)..., displayedComponents: .date)
                    .labelsHidden().environment(\.locale, Locale(identifier: "ko_KR")).tint(DearbyStyle.teal)
                    .accessibilityLabel(required ? "\(label), 필수" : label)
            } else {
                Button("날짜 선택") { date.wrappedValue = max(.now, earliest ?? .distantPast) }.font(.dearby(.subheadline).weight(.semibold))
                    .foregroundStyle(DearbyStyle.teal).frame(minHeight: 44).accessibilityLabel("\(label) 선택\(required ? ", 필수" : "")")
            }
        }.frame(minHeight: 44)
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
        VStack(alignment: .leading, spacing: 24) {
            ProfileContactFields(phone: $phone, email: $email, extras: $extras,
                                 phoneError: "전화번호는 숫자 8~15자리로 입력해 주세요.") { kind in
                extras.append(ProfileExtraContact(id: UUID().uuidString, kind: kind, value: ""))
            }
            HistoryPeriodFields(start: $start, end: $end, ongoing: $ongoing)
        }.padding(20).background(.white)
    }
}

#Preview("프로필 입력") { ScrollView { ProfileFieldsSample() } }
#endif
