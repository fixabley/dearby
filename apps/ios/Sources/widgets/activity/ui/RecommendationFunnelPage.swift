import SwiftUI

/// 추천 설정 퍼널 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24; iOS는 기본 목록과 `.borderedProminent` 큰 버튼): 0 시작 → 1 관심 분야
/// (분야 태그를 상위 분야별 섹션) → 2 활동 종류 → 3 얻고 싶은 것(성격 태그) → 4 결과, 한 화면에 질문 하나. 단계는 시스템 내비게이션으로 쌓여 뒤로·밀어서 뒤로가
/// 된다. `나중에`와 끝까지 건너뛰기도 완료로 기록한다(다시 띄우지 않음). 다시 열면(이미 마친 뒤) 시작 화면 없이 지금 답을 고른 채 1부터. 개인정보는 묻지 않는다.
struct RecommendationFunnelPage: View {
    let model: RecommendationModel
    /// `추천 보러 가기`: the app shows the 발견 tab.
    var done: () -> Void = {}
    @State private var answers: AccountClient.RecommendationPreferences
    @State private var path: [Step] = []
    private let reopened: Bool
    @Environment(\.dismiss) private var dismiss
    enum Step: Hashable { case fields, categories, traits, result }
    /// 찾는 활동 종류: the funnel's wording for existing category codes only (멘토십 has no code and is left out; 멘토링 is a 성격 tag).
    private static let categories: [(code: ActivityCategory, label: String)] = [
        (.bootcamp, "부트캠프"), (.club, "연합동아리"), (.contest, "대회"), (.hackathon, "해커톤"), (.conference, "컨퍼런스"), (.study, "스터디")
    ]
    init(model: RecommendationModel, done: @escaping () -> Void = {}) {
        self.model = model
        self.done = done
        _answers = State(initialValue: model.preferences ?? .init())
        reopened = model.preferences?.completedAt != nil
    }
    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if reopened {
                    page(.fields).toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
                } else {
                    intro
                }
            }
                .navigationDestination(for: Step.self) { page($0) }
        }
        .tint(DearbyStyle.teal)
        .task { await model.loadTags() }
    }
    private var intro: some View {
        DearbyQuestionPage(progress: 0, title: "나에게 맞는 활동을 찾아 드릴게요", message: "질문 3개, 30초면 끝나요. 답하지 않은 건 그냥 넘어가도 돼요.",
                           button: "시작하기", next: { path.append(.fields) }) {}
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("나중에") { model.finish(.init()); dismiss() }.accessibilityIdentifier("reco-later")
                }
            }
    }
    @ViewBuilder private func page(_ step: Step) -> some View {
        switch step {
        case .fields:
            question(0.25, "어떤 분야에 관심 있어요?", "여러 개 골라도 돼요.", then: .categories) { fieldSections }
        case .categories:
            question(0.5, "어떤 활동을 찾고 있어요?", "여러 개 골라도 돼요.", then: .traits) {
                Section { ForEach(Self.categories, id: \.code) { item in check(item.label, item.code.rawValue, in: \.categories) } }
            }
        case .traits:
            question(0.75, "활동하면서 무엇을 얻고 싶어요?", "고르지 않아도 괜찮아요.", then: .result) {
                Section { ForEach(model.tags.filter { !$0.isField }) { tag in check(tag.name, tag.id, in: \.traitTagIds) } }
            }
        case .result:
            result
        }
    }
    private func question(_ progress: Double, _ title: String, _ message: String, then next: Step,
                          @ViewBuilder rows: () -> some View) -> some View {
        DearbyQuestionPage(progress: progress, title: title, message: message, button: "다음", next: { go(next) }, content: rows)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("건너뛰기") { go(next) } } }
    }
    /// Entering the result records the answers (`finish`) and loads their recommendations.
    private func go(_ step: Step) {
        if step == .result { model.finish(answers) }
        path.append(step)
    }
    /// 분야 tags under their 상위 분야: a top-level 분야 is its section's first row, its 하위 분야 follow.
    @ViewBuilder private var fieldSections: some View {
        let fields = model.tags.filter(\.isField)
        let roots = fields.filter { tag in tag.parentID == nil || !fields.contains { $0.id == tag.parentID } }
        if fields.isEmpty {
            Section { Text(model.tags.isEmpty ? "분야를 불러오고 있어요" : "고를 수 있는 분야가 아직 없어요").foregroundStyle(.secondary) }
        }
        ForEach(roots) { root in
            Section(root.name) {
                check(fields.contains { $0.parentID == root.id } ? "\(root.name) 전체" : root.name, root.id, in: \.fieldTagIds)
                ForEach(fields.filter { $0.parentID == root.id }) { child in check(child.name, child.id, in: \.fieldTagIds) }
            }
        }
    }
    private func check<Value: Hashable>(_ label: String, _ value: Value,
                                        in list: WritableKeyPath<AccountClient.RecommendationPreferences, [Value]>) -> some View {
        DearbyCheckRow(isOn: Binding(get: { answers[keyPath: list].contains(value) }, set: { on in
            if on { answers[keyPath: list].append(value) } else { answers[keyPath: list].removeAll { $0 == value } }
        })) { Text(label) }
    }
    private var result: some View {
        let count = model.items.count
        let title = model.loading ? "맞는 활동을 찾고 있어요" : count == 0 ? "지금 지원할 수 있는 활동을 찾지 못했어요" : "지금 지원할 수 있는 활동 \(count)개를 찾았어요"
        return DearbyQuestionPage(progress: 1, title: title, message: picked.isEmpty ? nil : picked, button: "추천 보러 가기", busy: model.loading,
                                  next: { dismiss(); done() }) {
            Section {
                if model.loading {
                    DearbyRowsSkeleton()
                } else {
                    ForEach(model.items.prefix(3)) { item in RecommendationRow(item: item) }
                }
            } footer: {
                if !model.hasEligibility { Text("지원 자격까지 맞추려면 프로필 › 지원 자격 정보를 입력해요").font(.dearby(.subheadline)) }
            }
        }
        .navigationBarBackButtonHidden(model.loading)
    }
    /// What was picked, in the funnel's order (`데이터·AI · 부트캠프`).
    private var picked: String {
        let names = Dictionary(model.tags.map { ($0.id, $0.name) }, uniquingKeysWith: { first, _ in first })
        let categories = Self.categories.filter { answers.categories.contains($0.code.rawValue) }.map(\.label)
        return (answers.fieldTagIds.compactMap { names[$0] } + categories + answers.traitTagIds.compactMap { names[$0] }).joined(separator: " · ")
    }
}

/// One 추천 item: activity name, program, and the server's reason chips.
struct RecommendationRow: View {
    let item: RecommendedActivity
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(item.activity.title).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(.primary).multilineTextAlignment(.leading)
            if let program = item.programName { Text(program).font(.dearby(.footnote)).foregroundStyle(.secondary) }
            if !item.reasons.isEmpty {
                ChipFlow(spacing: 6) { ForEach(item.reasons, id: \.self) { DearbyBadge(title: $0, quiet: true) } }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}
