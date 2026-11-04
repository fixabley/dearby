import SwiftUI

/// Step-by-step history entry (contract "이력 (History) — 역할 매칭", 2026-11-03 one funnel: 어디에서 → 역할 → 기간 → 설명): one question per
/// screen with progress, then a summary with evidence.
/// Each step is an inset-grouped `Form` section whose header is the question and whose footer carries the error
/// (contract "폼은 플랫폼 기본 컴포넌트"). Steps are pushed on the page's own navigation stack, so the system back button and the edge
/// swipe go to the previous question with the answers kept (contract "뒤로 가기", as the 추천 funnel). The root (the first question, or a saved
/// history's summary) has 닫기, which asks before dropping changes; pulling the sheet down is off while there are changes.
/// `onDone` hands the draft back to the profile form; evidence uploads go straight to
/// the server for saved histories. The page owns the search focus of its pickers and the description row's focus so the keyboard 완료 clears both.
struct HistoryFunnelPage: View {
    @State var draft: HistoryDraft
    let state: ProfileModel
    /// True when the history already exists on the server, so evidence can be attached now.
    let saved: Bool
    let onDone: (HistoryDraft) -> Void
    /// The first question, or the summary when the history is opened saved; later steps are pushed on `path`.
    private let root: HistoryDraft.Step
    private let original: HistoryDraft
    @State private var path: [HistoryDraft.Step] = []
    @State private var tried = false
    @State private var discarding = false
    @FocusState private var searchFocused: Bool
    @FocusState private var field: String?
    @Environment(\.dismiss) private var dismiss
    init(draft: HistoryDraft, state: ProfileModel, saved: Bool, startAtSummary: Bool = false, onDone: @escaping (HistoryDraft) -> Void) {
        _draft = State(initialValue: draft)
        original = draft
        self.state = state
        self.saved = saved
        self.onDone = onDone
        root = startAtSummary ? .summary : .owner
    }
    private let steps = HistoryDraft.Step.allCases
    /// Evidence is uploaded as it is picked, so only the answers count as changes.
    private var edited: Bool {
        var compared = draft
        compared.evidence = original.evidence
        compared.verification = original.verification
        return compared != original
    }
    var body: some View {
        NavigationStack(path: $path) {
            page(root)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { if edited { discarding = true } else { dismiss() } } } }
                .navigationDestination(for: HistoryDraft.Step.self) { page($0) }
        }
        .interactiveDismissDisabled(edited)
        .confirmationDialog("작성 중인 내용을 버릴까요?", isPresented: $discarding, titleVisibility: .visible) {
            Button("버리기", role: .destructive) { dismiss() }
            Button("취소", role: .cancel) {}
        }
        .onChange(of: path) { _, _ in tried = false }
        // A role belongs to its unit, so changing the unit (also un-picking it) starts the role over; un-picking drops the unit's refs too.
        .onChange(of: draft.owner) { _, next in
            draft.role = nil; draft.roleName = ""
            if next == nil { draft.organization = nil; draft.program = nil; draft.activity = nil }
        }
    }
    private func page(_ step: HistoryDraft.Step) -> some View {
        let index = steps.firstIndex(of: step) ?? 0
        return VStack(spacing: 0) {
            ProgressView(value: Double(index + 1), total: Double(steps.count)).padding(.horizontal, 20).padding(.top, 8)
                .accessibilityLabel("\(index + 1) / \(steps.count) 단계")
            Form { content(step) }
                .font(.dearby(.body))
                .safeAreaInset(edge: .bottom) { bar(step) }
        }
        .dearbyDismissKeyboard { searchFocused = false; field = nil }.background(Color(.systemGroupedBackground))
        .navigationTitle(step == .summary ? "이력 확인" : "이력 입력").navigationBarTitleDisplayMode(.inline)
    }
    @ViewBuilder private func content(_ step: HistoryDraft.Step) -> some View {
        let stepError = tried ? draft.error(at: step) : nil
        switch step {
        case .owner:
            // 조직·프로그램·활동 in one search; a picked unit keeps its kind, a typed name asks for its kind below.
            ActivityInfoPickerView(title: "어디에서 활동했나요?", prompt: "조직·프로그램·활동 이름 검색", kind: .units, account: state.account,
                                   picked: Binding(get: { draft.owner.flatMap { unit in unit.id.map { ActivityInfoRef(id: $0, name: unit.name) } } },
                                                   set: { if $0 == nil { draft.owner = nil } }),
                                   pickedDetail: draft.owner?.kindName, typed: $draft.ownerName, focus: $searchFocused, footer: stepError) { draft.pick($0) }
            if draft.owner == nil, !draft.ownerName.isEmpty {
                Section {
                    Picker("종류", selection: $draft.ownerKind) {
                        ForEach(["organization", "program", "activity"], id: \.self) { Text(HistoryOwner.kindName($0)).tag($0) }
                    }.pickerStyle(.segmented)
                } header: { Text("직접 입력한 곳의 종류").font(.dearby(.subheadline)) }
            }
        case .role where draft.owner != nil:
            // The unit's roles (an empty search lists them all); a name not in the list is typed in the same picker.
            ActivityInfoPickerView(title: "어떤 역할이었나요?", prompt: "역할 검색 (예: 운영진, 참가자)", kind: .roles, owner: draft.owner,
                                   account: state.account, picked: $draft.role, typed: $draft.roleName, focus: $searchFocused, footer: stepError)
        case .role:
            // A typed unit has no role list, so the role is typed too.
            Section {
                DearbyFormRow(label: "역할", required: true, text: $draft.roleName, focus: $field, value: "roleName")
            } header: { question("어떤 역할이었나요?") } footer: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("직접 입력한 항목은 관리자 승인 뒤 명함에 넣을 수 있어요.")
                    if let stepError { DearbyFieldError(text: stepError) }
                }.font(.dearby(.subheadline))
            }
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
                DearbyFormRow(label: "설명", text: $draft.description, multiline: true, focus: $field, value: "description")
            } header: { question("한 줄로 설명해 주세요 (선택)") }
        case .summary:
            summary
        }
    }
    @ViewBuilder private var summary: some View {
        Section {
            VStack(alignment: .leading, spacing: 4) {
                Text(draft.history.title).fontWeight(.semibold)
                Text(draft.summaryLine).font(.dearby(.footnote)).foregroundStyle(.secondary)
                if !draft.description.isEmpty { Text(draft.description).padding(.top, 4) }
            }.padding(.vertical, 4).accessibilityElement(children: .combine)
            // Back to the first question: pushed over a saved history's summary, else the root itself.
            Button { path = root == .summary ? [.owner] : [] } label: { Text("내용 고치기").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle()) }
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
    /// `다음`/`완료` only: the previous question is the navigation bar's back.
    private func bar(_ step: HistoryDraft.Step) -> some View {
        Button {
            tried = true
            guard draft.error(at: step) == nil else { return }
            tried = false
            if step == .summary {
                onDone(draft)
                dismiss()
            } else if let next = steps.drop(while: { $0 != step }).dropFirst().first {
                // A saved history's summary is the root: going back to it pops the edited questions.
                if next == root { path = [] } else { path.append(next) }
            }
        } label: { Text(step == .summary ? "완료" : "다음").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).tint(DearbyStyle.tealFill)
            .controlSize(.large).font(.dearby(.body).weight(.semibold)).padding(.horizontal, 20).padding(.vertical, 12).background(.bar)
    }
    private func question(_ text: String) -> some View {
        Text(text).font(.dearby(.title3).bold()).foregroundStyle(.primary)
    }
}
