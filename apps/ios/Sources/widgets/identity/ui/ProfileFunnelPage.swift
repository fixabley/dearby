import SwiftUI

/// First profile entry as an accordion (2026-10-17 user decision): steps stack in order, only one is open, and answered
/// steps collapse to a row that reopens on tap. Rows are platform form rows (contract "폼은 플랫폼 기본 컴포넌트" 2026-10-19 밤):
/// an inset-grouped `Form`, the question as the open section's header, help and errors in its footer, the keyboard open as
/// a step appears. Rows stack the label over a `.title3` input (`DearbyFormRow`, contract "폼 라벨·입력 크기" 2026-10-20) and the whole row
/// focuses the field; the keyboard 완료 clears both focus states. The tab bar is hidden meanwhile (`HomePage.hidesTabs`). Shown until the profile is saved once.
/// It is the tab's root, so there is no navigation back: `이전` beside `다음` reopens the step before the open one with every answer kept (contract "뒤로 가기").
struct ProfileFunnelPage: View {
    @Binding var form: ProfileForm
    let account: AccountViewModel
    let saving: Bool
    let error: String?
    let save: () -> Void
    @State private var open = Step.name
    @State private var reached: Set<Step> = [.name]
    @State private var tried = false
    @FocusState private var focus: Step?
    @FocusState private var jobSearch: Bool
    enum Step: CaseIterable {
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
    private var allReached: Bool { reached.count == Step.allCases.count }
    private var reachedSteps: [Step] { Step.allCases.filter { reached.contains($0) } }
    private var previous: Step? { Step.allCases.firstIndex(of: open).flatMap { $0 > 0 ? Step.allCases[$0 - 1] : nil } }
    var body: some View {
        VStack(spacing: 0) {
            Form {
                // Answered steps before and after the open one are grouped on either side of it.
                doneRows(Array(reachedSteps.prefix { $0 != open }))
                openSection(open)
                doneRows(Array(reachedSteps.drop { $0 != open }.dropFirst()))
            }
            .font(.dearby(.body))
            .safeAreaInset(edge: .bottom) { bar }
        }
        .dearbyDismissKeyboard { focus = nil; jobSearch = false }.background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("프로필 만들기").navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: focusOpen)
        .onChange(of: open) { _, _ in focusOpen() }
    }
    private func openSection(_ step: Step) -> some View {
        Section {
            switch step {
            case .name:
                DearbyFormRow(label: step.label, required: true, text: $form.name, focus: $focus, value: step) {
                    $0.textContentType(.name).submitLabel(.next).onSubmit(next)
                }
            case .jobs:
                JobTagPicker(form: $form, account: account, focus: $jobSearch)
            case .phone:
                DearbyFormRow(label: step.label, symbol: ContactModel.symbol(kind: "phone"), required: true, text: $form.phone, focus: $focus, value: step) {
                    $0.keyboardType(.phonePad).textContentType(.telephoneNumber)
                }
            case .email:
                DearbyFormRow(label: step.label, symbol: ContactModel.symbol(kind: "email"), required: true, text: $form.email, focus: $focus, value: step) {
                    $0.keyboardType(.emailAddress).textContentType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .submitLabel(.next).onSubmit(next)
                }
            case .introduction:
                DearbyFormRow(label: step.label, text: $form.introduction, multiline: true, focus: $focus, value: step)
            }
        } header: {
            Text(question(step)).font(.dearby(.title3).bold()).foregroundStyle(.primary)
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                if let hint = hint(step) { Text(hint) }
                if tried, let message = error(at: step) { DearbyFieldError(text: message) }
            }.font(.dearby(.subheadline))
        }
    }
    /// Answered steps: label over value, the same shape as an input row; the whole row reopens the step.
    @ViewBuilder private func doneRows(_ steps: [Step]) -> some View {
        if !steps.isEmpty {
            Section {
                ForEach(steps, id: \.self) { step in
                    Button { tried = false; open = step } label: {
                        DearbyValueRow(label: step.label, value: value(of: step)).lineLimit(2).contentShape(Rectangle())
                    }.accessibilityHint("눌러서 고쳐요")
                }
            }
        }
    }
    private var bar: some View {
        VStack(spacing: 8) {
            if let error { DearbyFieldError(text: error).frame(maxWidth: .infinity, alignment: .leading) }
            HStack(spacing: 10) {
                if let previous {
                    Button { tried = false; open = previous } label: { Text("이전").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
                }
                Button(action: next) {
                    Group { if saving { ProgressView().tint(DearbyStyle.onTeal) } else { Text(allReached ? "프로필 저장" : "다음") } }
                        .frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent).tint(DearbyStyle.tealFill).accessibilityIdentifier("profile-setup-next")
            }.controlSize(.large).font(.dearby(.body).weight(.semibold)).disabled(saving)
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.bar)
    }
    private func next() {
        tried = true
        guard error(at: open) == nil else { return }
        tried = false
        if let unreached = Step.allCases.first(where: { !reached.contains($0) }) {
            reached.insert(unreached)
            open = unreached
        } else {
            save()
        }
    }
    /// One text field per step, so the keyboard opens as the step appears (the 직무 step has its own search field).
    private func focusOpen() {
        guard open != .jobs else { focus = nil; return }
        Task { @MainActor in try? await Task.sleep(for: .milliseconds(80)); focus = open }
    }
    private func error(at step: Step) -> String? {
        switch step {
        case .name: form.nameError
        case .phone: form.phoneError
        case .email: form.emailError
        default: nil
        }
    }
    private func value(of step: Step) -> String {
        switch step {
        case .name: form.name
        case .jobs: form.jobsText
        case .phone: form.phone
        case .email: form.email
        case .introduction: form.introduction
        }
    }
    private func question(_ step: Step) -> String {
        switch step {
        case .name: "이름이 어떻게 되세요?"
        case .jobs: "어떤 일을 하세요? (선택)"
        case .phone: "연락받을 전화번호를 알려 주세요"
        case .email: "이메일 주소는요?"
        case .introduction: "처음 만난 사람에게 한두 문장으로 소개해 주세요 (선택)"
        }
    }
    private func hint(_ step: Step) -> String? {
        switch step {
        case .name: "명함과 공개 프로필에 이 이름으로 보여요."
        case .jobs: ["검색해서 여러 개 고를 수 있어요. 명함에 직무로 보여요.", JobTagPicker.note(for: form)].compactMap { $0 }.joined(separator: " ")
        case .phone: "명함에 넣을지는 명함을 만들 때 골라요."
        case .email: nil
        case .introduction: allReached ? "활동 이력은 저장한 뒤 단계별로 추가할 수 있어요." : nil
        }
    }
}
