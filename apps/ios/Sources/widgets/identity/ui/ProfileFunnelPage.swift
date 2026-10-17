import SwiftUI

/// First profile entry, Toss-style (Mobbin: Toss 송금 퍼널 — one question per screen, a big question headline, one
/// focused field, a bottom button). Shown in the 내 프로필 tab until the profile is saved once; then the inline editor
/// takes over and histories are added there with `HistoryFunnelPage`.
struct ProfileFunnelPage: View {
    @Binding var form: ProfileForm
    let saving: Bool
    let error: String?
    let save: () -> Void
    @State private var step = Step.name
    @State private var tried = false
    enum Step: CaseIterable { case name, job, phone, email, introduction, summary }
    private var index: Int { Step.allCases.firstIndex(of: step) ?? 0 }
    var body: some View {
        VStack(spacing: 0) {
            Text("프로필 만들기").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 20).padding(.top, 16).accessibilityAddTraits(.isHeader)
            ProgressView(value: Double(index + 1), total: Double(Step.allCases.count)).tint(DearbyStyle.teal).padding(.horizontal, 20).padding(.top, 8)
                .accessibilityLabel("\(index + 1) / \(Step.allCases.count) 단계")
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    content
                    if tried, let message = error(at: step) { DearbyFieldError(text: message) }
                }.padding(20)
            }
            bar
        }
    }
    @ViewBuilder private var content: some View {
        switch step {
        case .name:
            question("이름이 어떻게 되세요?")
            DearbyInlineField(label: "이름, 필수", text: $form.name, editing: true, prompt: "실명 또는 활동명", autofocus: true)
                .textContentType(.name).submitLabel(.next).onSubmit(next)
            hint("명함과 공개 프로필에 이 이름으로 보여요.")
        case .job:
            question("어떤 일을 하세요? (선택)")
            DearbyInlineField(label: "직함", text: $form.job, editing: true, prompt: "예: 서비스 기획 · 커뮤니티", autofocus: true)
                .textContentType(.jobTitle).submitLabel(.next).onSubmit(next)
            hint("명함에 직함으로 보여요. 나중에 적어도 돼요.")
        case .phone:
            question("연락받을 전화번호를 알려 주세요")
            DearbyInlineField(label: "전화번호, 필수", text: $form.phone, editing: true, prompt: "010-0000-0000", autofocus: true)
                .keyboardType(.phonePad).textContentType(.telephoneNumber)
            hint("명함에 넣을지는 명함을 만들 때 골라요.")
        case .email:
            question("이메일 주소는요?")
            DearbyInlineField(label: "이메일, 필수", text: $form.email, editing: true, prompt: "name@example.com", autofocus: true)
                .keyboardType(.emailAddress).textContentType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                .submitLabel(.next).onSubmit(next)
        case .introduction:
            question("처음 만난 사람에게 한두 문장으로 소개해 주세요 (선택)")
            DearbyInlineField(label: "소개", text: $form.introduction, editing: true, prompt: "예: 커뮤니티 운영과 행사 기획을 해요", multiline: true, autofocus: true)
        case .summary:
            question("이렇게 저장할까요?")
            VStack(alignment: .leading, spacing: 10) {
                row("이름", form.name)
                row("직함", form.job)
                row("전화번호", form.phone)
                row("이메일", form.email)
                row("소개", form.introduction)
            }.padding(16).background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 12))
            Button("내용 고치기") { step = .name }.font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
            hint("활동 이력은 저장한 뒤 단계별로 추가할 수 있어요.")
            if let error { Text(error).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.danger) }
        }
    }
    private var bar: some View {
        HStack(spacing: 10) {
            if index > 0 {
                Button("이전") { tried = false; step = Step.allCases[index - 1] }.buttonStyle(DearbyButtonStyle(outlined: true)).disabled(saving)
            }
            Button(action: next) {
                if saving { ProgressView().tint(.white) } else { Text(step == .summary ? "프로필 저장" : "다음") }
            }.buttonStyle(DearbyButtonStyle()).disabled(saving).accessibilityIdentifier("profile-setup-next")
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.white).overlay(alignment: .top) { Rectangle().fill(DearbyStyle.line).frame(height: 1) }
    }
    private func next() {
        tried = true
        guard error(at: step) == nil else { return }
        tried = false
        if step == .summary { save() } else { step = Step.allCases[index + 1] }
    }
    private func error(at step: Step) -> String? {
        switch step {
        case .name: form.nameError
        case .phone: form.phoneError
        case .email: form.emailError
        default: nil
        }
    }
    private func question(_ text: String) -> some View {
        Text(text).font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.ink).accessibilityAddTraits(.isHeader)
    }
    private func hint(_ text: String) -> some View {
        Text(text).font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
    }
    private func row(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(label).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet).frame(width: 64, alignment: .leading)
            Text(value.isEmpty ? "비워 둠" : value).font(.dearby(.body)).foregroundStyle(value.isEmpty ? DearbyStyle.quiet : DearbyStyle.ink)
        }.accessibilityElement(children: .combine)
    }
}
