import SwiftUI

/// First profile entry, Toss-style accordion (2026-10-17 user decision): sections stack in order, only one is open, and
/// answered sections collapse to a tall row that reopens on tap. Bigger type and 56pt touch targets than the editor; the
/// tab bar is hidden meanwhile (`HomePage.hidesTabs`). Shown until the profile is saved once; histories come afterwards.
struct ProfileFunnelPage: View {
    @Binding var form: ProfileForm
    let account: AccountViewModel
    let saving: Bool
    let error: String?
    let save: () -> Void
    @State private var open = Section.name
    @State private var reached: Set<Section> = [.name]
    @State private var tried = false
    enum Section: CaseIterable {
        case name, jobs, phone, email, introduction
        var label: String {
            switch self {
            case .name: "이름"
            case .jobs: "직무"
            case .phone: "전화번호"
            case .email: "이메일"
            case .introduction: "소개"
            }
        }
    }
    private var allReached: Bool { reached.count == Section.allCases.count }
    var body: some View {
        VStack(spacing: 0) {
            Text("프로필 만들기").font(.dearby(.callout).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 24).padding(.vertical, 12).accessibilityAddTraits(.isHeader)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Section.allCases.filter { reached.contains($0) }, id: \.self) { section in
                        if section == open { openSection(section) } else { doneRow(section) }
                    }
                    if allReached { hint("활동 이력은 저장한 뒤 단계별로 추가할 수 있어요.") }
                    if let error { Text(error).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.danger) }
                }.padding(.horizontal, 24).padding(.bottom, 24)
            }.scrollDismissesKeyboard(.interactively)
            bar
        }
    }
    @ViewBuilder private func openSection(_ section: Section) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            switch section {
            case .name:
                question("이름이 어떻게 되세요?")
                field(label: "이름, 필수", text: $form.name, prompt: "실명 또는 활동명").textContentType(.name).submitLabel(.next).onSubmit(next)
                hint("명함과 공개 프로필에 이 이름으로 보여요.")
            case .jobs:
                question("어떤 일을 하세요? (선택)")
                hint("검색해서 여러 개 고를 수 있어요. 명함에 직무로 보여요.")
                JobTagPicker(form: $form, account: account)
            case .phone:
                question("연락받을 전화번호를 알려 주세요")
                field(label: "전화번호, 필수", text: $form.phone, prompt: "010-0000-0000").keyboardType(.phonePad).textContentType(.telephoneNumber)
                hint("명함에 넣을지는 명함을 만들 때 골라요.")
            case .email:
                question("이메일 주소는요?")
                field(label: "이메일, 필수", text: $form.email, prompt: "name@example.com")
                    .keyboardType(.emailAddress).textContentType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled().submitLabel(.next).onSubmit(next)
            case .introduction:
                question("처음 만난 사람에게 한두 문장으로 소개해 주세요 (선택)")
                field(label: "소개", text: $form.introduction, prompt: "예: 커뮤니티 운영과 행사 기획을 해요", multiline: true)
            }
            if tried, let message = error(at: section) { DearbyFieldError(text: message) }
        }.padding(.vertical, 8)
    }
    /// An answered section: label and value on a 56pt row; tapping reopens it.
    private func doneRow(_ section: Section) -> some View {
        let value = value(of: section)
        return Button { tried = false; open = section } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(section.label).font(.dearby(.footnote).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                    Text(value.isEmpty ? "비워 둠" : value).font(.dearby(.headline)).foregroundStyle(value.isEmpty ? DearbyStyle.quiet : DearbyStyle.ink).lineLimit(2)
                }
                Spacer()
                Image(systemName: "pencil").foregroundStyle(DearbyStyle.quiet)
            }.padding(.horizontal, 16).padding(.vertical, 12).frame(maxWidth: .infinity, minHeight: 56).background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 14))
        }.buttonStyle(.plain).accessibilityHint("눌러서 고쳐요")
    }
    private var bar: some View {
        Button(action: next) {
            if saving { ProgressView().tint(.white) } else { Text(allReached ? "프로필 저장" : "다음") }
        }.buttonStyle(DearbyButtonStyle(height: 56)).disabled(saving).accessibilityIdentifier("profile-setup-next")
            .padding(.horizontal, 24).padding(.vertical, 12).background(Color(.systemBackground)).overlay(alignment: .top) { Rectangle().fill(DearbyStyle.line).frame(height: 1) }
    }
    private func next() {
        tried = true
        guard error(at: open) == nil else { return }
        tried = false
        if let unreached = Section.allCases.first(where: { !reached.contains($0) }) {
            reached.insert(unreached)
            open = unreached
        } else {
            save()
        }
    }
    private func error(at section: Section) -> String? {
        switch section {
        case .name: form.nameError
        case .phone: form.phoneError
        case .email: form.emailError
        default: nil
        }
    }
    private func value(of section: Section) -> String {
        switch section {
        case .name: form.name
        case .jobs: form.jobsText
        case .phone: form.phone
        case .email: form.email
        case .introduction: form.introduction
        }
    }
    private func question(_ text: String) -> some View {
        Text(text).font(.dearby(.title).weight(.bold)).foregroundStyle(DearbyStyle.ink).accessibilityAddTraits(.isHeader)
    }
    private func hint(_ text: String) -> some View {
        Text(text).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
    }
    /// The funnel's field: one size up from the editor, keyboard open as the section appears.
    private func field(label: String, text: Binding<String>, prompt: String, multiline: Bool = false) -> some View {
        DearbyInlineField(label: label, text: text, editing: true, prompt: prompt, multiline: multiline, font: .dearby(.title3), autofocus: true)
    }
}
