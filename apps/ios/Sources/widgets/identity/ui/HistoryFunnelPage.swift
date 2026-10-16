import SwiftUI

/// Toss-style history entry: one question per screen with progress, then a summary with evidence (contract "이력").
/// `onDone` hands the draft back to the profile form; evidence uploads go straight to the server for saved histories.
struct HistoryFunnelPage: View {
    @State var draft: HistoryDraft
    let state: ProfileModel
    /// True when the history already exists on the server, so evidence can be attached now.
    let saved: Bool
    let onDone: (HistoryDraft) -> Void
    @State private var step: HistoryDraft.Step
    @State private var tried = false
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
    var body: some View {
        VStack(spacing: 0) {
            ProgressView(value: Double(index + 1), total: Double(steps.count)).tint(DearbyStyle.teal).padding(.horizontal, 20).padding(.top, 8)
                .accessibilityLabel("\(index + 1) / \(steps.count) 단계")
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    content
                    if tried, let error = draft.error(at: step) { DearbyFieldError(text: error) }
                }.padding(20)
            }
            bar
        }
        .background(.white).navigationTitle(step == .summary ? "이력 확인" : "이력 입력").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
    }
    @ViewBuilder private var content: some View {
        switch step {
        case .kind:
            Text("어떤 이력인가요?").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
            choice("조직 활동", "동아리·학회·회사 같은 조직에서 활동했어요", selected: !draft.isActivity) { draft.kind = "organization" }
            choice("활동 참여", "컨퍼런스·해커톤·캠프 같은 활동에 참여했어요", selected: draft.isActivity) { draft.kind = "activity" }
        case .organization:
            ActivityInfoPickerView(title: "어떤 조직인가요?", prompt: "조직 이름 검색", kind: .organizations, parentID: nil, account: state.account,
                                   picked: $draft.organization, typed: $draft.organizationName)
        case .program:
            ActivityInfoPickerView(title: draft.isActivity ? "어떤 프로그램인가요?" : "어떤 프로그램인가요? (선택)", prompt: "프로그램 이름 검색",
                                   kind: .programs, parentID: draft.organization?.id, account: state.account, picked: $draft.program, typed: $draft.programName)
        case .activity:
            ActivityInfoPickerView(title: "어떤 활동인가요?", prompt: "활동 이름 검색", kind: .activities, parentID: draft.program?.id,
                                   account: state.account, picked: $draft.activity, typed: $draft.activityName)
        case .role:
            ActivityInfoPickerView(title: "어떤 역할이었나요?", prompt: "역할 검색 (예: 운영진, 참가자)", kind: .roles, parentID: nil,
                                   account: state.account, picked: $draft.role, typed: $draft.roleName)
        case .period:
            Text("언제였나요?").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
            HistoryPeriodFields(start: $draft.start, end: $draft.end, ongoing: $draft.ongoing)
        case .description:
            Text("한 줄로 설명해 주세요 (선택)").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
            DearbyInlineField(label: "설명", text: $draft.description, editing: true, prompt: "맡은 일이나 성과", multiline: true)
        case .summary:
            summary
        }
    }
    private var summary: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(draft.chain.joined(separator: " · ")).font(.dearby(.title3).bold()).accessibilityAddTraits(.isHeader)
            Text("\(draft.roleTitle) · \(draft.summaryLine)").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
            if !draft.description.isEmpty { Text(draft.description).font(.dearby(.body)) }
            if draft.history.hasCustomEntry {
                Label(draft.approval == "rejected" ? "거절됨" + (draft.approvalReason.map { " · " + $0 } ?? "") : "직접 입력한 항목은 관리자 승인 뒤 명함에 넣을 수 있어요",
                      systemImage: draft.approval == "rejected" ? "xmark.octagon" : "clock")
                    .font(.dearby(.footnote)).foregroundStyle(draft.approval == "rejected" ? DearbyStyle.danger : DearbyStyle.quiet)
            }
            Button("내용 고치기") { step = .kind }.font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
            Divider()
            Text("인증자료").font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
            Text(draft.verification == "verified" ? "관리자가 확인했어요." : "건너뛰어도 돼요. 확인되기 전까지 ‘미확인’으로 보여요.")
                .font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
            if saved {
                EvidenceSection(historyID: draft.id, items: draft.evidence, state: state) { updated in draft.evidence = updated }
            } else {
                Text("프로필을 저장한 뒤 이 이력을 열면 인증자료를 올릴 수 있어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
            }
        }
    }
    private var bar: some View {
        HStack(spacing: 10) {
            if index > 0 && step != .summary {
                Button("이전") { step = steps[index - 1] }.buttonStyle(DearbyButtonStyle(outlined: true))
            }
            Button(step == .summary ? "완료" : "다음") {
                tried = true
                guard draft.error(at: step) == nil else { return }
                tried = false
                if step == .summary {
                    onDone(draft)
                    dismiss()
                } else {
                    step = steps[index + 1]
                }
            }.buttonStyle(DearbyButtonStyle())
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.white).overlay(alignment: .top) { Rectangle().fill(DearbyStyle.line).frame(height: 1) }
    }
    private func choice(_ title: String, _ detail: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink)
                    Text(detail).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: selected ? "checkmark.circle.fill" : "circle").foregroundStyle(selected ? DearbyStyle.teal : DearbyStyle.line)
            }.padding(16).background(selected ? DearbyStyle.mint : DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 12))
        }.buttonStyle(.plain).accessibilityAddTraits(selected ? .isSelected : [])
    }
}
