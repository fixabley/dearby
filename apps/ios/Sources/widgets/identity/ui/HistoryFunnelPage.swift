import SwiftUI

/// Step-by-step history entry: one question per screen with progress, then a summary with evidence (contract "이력").
/// Each step is an inset-grouped `Form` section whose header is the question and whose footer carries the error
/// (contract "폼은 플랫폼 기본 컴포넌트"). `onDone` hands the draft back to the profile form; evidence uploads go straight to
/// the server for saved histories. The page owns the search focus of its pickers and the description row's focus so the keyboard 완료 clears both.
struct HistoryFunnelPage: View {
    @State var draft: HistoryDraft
    let state: ProfileModel
    /// True when the history already exists on the server, so evidence can be attached now.
    let saved: Bool
    let onDone: (HistoryDraft) -> Void
    @State private var step: HistoryDraft.Step
    @State private var tried = false
    @FocusState private var searchFocused: Bool
    @FocusState private var field: String?
    @Environment(\.dismiss) private var dismiss
    init(draft: HistoryDraft, state: ProfileModel, saved: Bool, startAtSummary: Bool = false, onDone: @escaping (HistoryDraft) -> Void) {
        _draft = State(initialValue: draft)
        self.state = state
        self.saved = saved
        self.onDone = onDone
        _step = State(initialValue: startAtSummary ? .summary : .kind)
    }
    private var steps: [HistoryDraft.Step] { draft.steps }
    private var index: Int { steps.firstIndex(of: step) ?? 0 }
    private var stepError: String? { tried ? draft.error(at: step) : nil }
    var body: some View {
        VStack(spacing: 0) {
            ProgressView(value: Double(index + 1), total: Double(steps.count)).padding(.horizontal, 20).padding(.top, 8)
                .accessibilityLabel("\(index + 1) / \(steps.count) 단계")
            Form { content }
                .font(.dearby(.body))
                .safeAreaInset(edge: .bottom) { bar }
        }
        .dearbyDismissKeyboard { searchFocused = false; field = nil }.background(Color(.systemGroupedBackground)).navigationTitle(step == .summary ? "이력 확인" : "이력 입력").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        // Un-picking an activity drops the organization and program it brought along, so a typed activity asks for them fresh.
        .onChange(of: draft.activity) { _, next in
            guard draft.isActivity, next == nil else { return }
            draft.organization = nil; draft.organizationName = ""
            draft.program = nil; draft.programName = ""
        }
    }
    @ViewBuilder private var content: some View {
        switch step {
        case .kind:
            Section {
                choice("조직 활동", "동아리·학회·회사 같은 조직에서 활동했어요", selected: !draft.isActivity) { draft.kind = "organization" }
                choice("활동 참여", "컨퍼런스·해커톤·캠프 같은 활동에 참여했어요", selected: draft.isActivity) { draft.kind = "activity" }
            } header: { question("어떤 이력인가요?") }
        case .organization:
            // 조직 이력의 첫 질문, 또는 직접 입력한 활동의 소속(contract "이력" 2026-10-18).
            ActivityInfoPickerView(title: draft.isActivity ? "어떤 조직에서 한 활동인가요?" : "어떤 조직인가요?", prompt: "조직 이름 검색",
                                   kind: .organizations, parentID: nil, account: state.account, picked: $draft.organization, typed: $draft.organizationName,
                                   focus: $searchFocused, footer: stepError)
        case .program:
            // Only reached for a hand-typed activity; 조직 이력 does not ask for a program.
            ActivityInfoPickerView(title: "어떤 프로그램인가요? (선택)", prompt: "프로그램 이름 검색",
                                   kind: .programs, parentID: draft.organization?.id, account: state.account, picked: $draft.program, typed: $draft.programName,
                                   focus: $searchFocused)
        case .activity:
            // Searches every published activity; a picked activity brings its organization and program along.
            ActivityInfoPickerView(title: "어떤 활동인가요?", prompt: "활동 이름 검색", kind: .activities, parentID: nil,
                                   account: state.account, picked: $draft.activity, typed: $draft.activityName, focus: $searchFocused, footer: stepError) { item in
                draft.organization = item.organization; draft.organizationName = ""
                draft.program = item.program; draft.programName = ""
            }
        case .role:
            ActivityInfoPickerView(title: "어떤 역할이었나요?", prompt: "역할 검색 (예: 운영진, 참가자)", kind: .roles, parentID: nil,
                                   account: state.account, picked: $draft.role, typed: $draft.roleName, focus: $searchFocused, footer: stepError)
        case .period:
            Section {
                HistoryPeriodFields(start: $draft.start, end: $draft.end, ongoing: $draft.ongoing)
            } header: { question("언제였나요?") } footer: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("시작일은 꼭 골라 주세요.")
                    if let stepError { DearbyFieldError(text: stepError) }
                }.font(.dearby(.subheadline))
            }
        case .description:
            Section {
                DearbyFormRow(label: "설명", focus: $field, value: "description") {
                    TextField("맡은 일이나 성과", text: $draft.description, axis: .vertical).lineLimit(3...6).accessibilityLabel("설명")
                }
            } header: { question("한 줄로 설명해 주세요 (선택)") }
        case .summary:
            summary
        }
    }
    @ViewBuilder private var summary: some View {
        Section {
            VStack(alignment: .leading, spacing: 4) {
                Text(draft.chain.joined(separator: " · ")).fontWeight(.semibold)
                Text("\(draft.roleTitle) · \(draft.summaryLine)").font(.dearby(.footnote)).foregroundStyle(.secondary)
                if !draft.description.isEmpty { Text(draft.description).padding(.top, 4) }
            }.padding(.vertical, 4).accessibilityElement(children: .combine)
            Button { step = .kind } label: { Text("내용 고치기").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle()) }
        } header: {
            Text("이력").font(.dearby(.subheadline))
        } footer: {
            if draft.history.hasCustomEntry {
                Label(draft.approval == "rejected" ? "거절됨" + (draft.approvalReason.map { " · " + $0 } ?? "") : "직접 입력한 항목은 관리자 승인 뒤 명함에 넣을 수 있어요",
                      systemImage: draft.approval == "rejected" ? "exclamationmark.triangle" : "clock")
                    .font(.dearby(.subheadline)).foregroundStyle(draft.approval == "rejected" ? DearbyStyle.danger : .secondary)
            }
        }
        let hint = draft.verification == "verified" ? "관리자가 확인했어요." : "건너뛰어도 돼요. 확인되기 전까지 ‘미확인’으로 보여요."
        if saved {
            EvidenceSection(historyID: draft.id, items: draft.evidence, state: state, hint: hint) { updated in draft.evidence = updated }
        } else {
            Section {
                Text("프로필을 저장한 뒤 이 이력을 열면 인증자료를 올릴 수 있어요.").foregroundStyle(.secondary)
            } header: { Text("인증자료").font(.dearby(.subheadline)) } footer: { Text(hint).font(.dearby(.subheadline)) }
        }
    }
    private var bar: some View {
        HStack(spacing: 10) {
            if index > 0 && step != .summary {
                Button { step = steps[index - 1] } label: { Text("이전").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
            }
            Button {
                tried = true
                guard draft.error(at: step) == nil else { return }
                tried = false
                if step == .summary {
                    onDone(draft)
                    dismiss()
                } else {
                    step = steps[index + 1]
                }
            } label: { Text(step == .summary ? "완료" : "다음").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).tint(DearbyStyle.tealFill)
        }.controlSize(.large).font(.dearby(.body).weight(.semibold)).padding(.horizontal, 20).padding(.vertical, 12).background(.bar)
    }
    private func question(_ text: String) -> some View {
        Text(text).font(.dearby(.title3).bold()).foregroundStyle(.primary)
    }
    /// A single-choice row with the system checkmark on the selected one.
    private func choice(_ title: String, _ detail: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(DearbyStyle.ink)
                    Text(detail).font(.dearby(.footnote)).foregroundStyle(.secondary).multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: selected ? "checkmark.circle.fill" : "circle").imageScale(.large).foregroundStyle(selected ? DearbyStyle.teal : .secondary)
            }.padding(.vertical, 2).frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
        }.accessibilityAddTraits(selected ? .isSelected : [])
    }
}
