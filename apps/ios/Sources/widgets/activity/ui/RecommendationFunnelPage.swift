import SwiftUI

/// 추천 설정 퍼널 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24; iOS는 기본 목록과 `.borderedProminent` 큰 버튼): 0 시작 → 1 관심 분야
/// (상위 분야와 상위가 없는 분야를 아이콘+글자 칩으로 여러 개) → 고른 상위 분야마다 하위 질문(3개 이상이면 한 화면에 묶음, contract "태그 아이콘+라벨·
/// 분야 1개·자동 펼침 없음" 2026-10-24) → 활동 종류 → 얻고 싶은 것(성격 태그) → 결과, 한 화면에 질문 하나. 단계는 시스템 내비게이션으로 쌓여 뒤로·밀어서
/// 뒤로가 된다. `나중에`와 끝까지 건너뛰기도 완료로 기록한다(다시 띄우지 않음). 다시 열면(이미 마친 뒤) 시작 화면 없이 지금 답을 고른 채 1부터.
/// 개인정보는 묻지 않는다. 분야 답은 저장 모양 그대로 `fieldTagIds` 하나다: 상위 id는 `{상위} 전부 좋아요`, 하위 id는 그 하위만, 하위 id만 있으면 그 상위를 고른 것.
struct RecommendationFunnelPage: View {
    let model: RecommendationModel
    /// `추천 보러 가기`: the app shows the 발견 tab.
    var done: () -> Void = {}
    @State private var answers: AccountClient.RecommendationPreferences
    @State private var path: [Step] = []
    private let reopened: Bool
    @Environment(\.dismiss) private var dismiss
    /// `subfields(id)`: one 상위 분야's 하위 question; `allSubfields`: every picked one's on one screen (three or more picked).
    enum Step: Hashable { case fields, subfields(Int64), allSubfields, categories, traits, result }
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
        DearbyQuestionPage(progress: 0, title: "나에게 맞는 활동을 찾아 드릴게요", message: "몇 가지 질문이면 끝나요. 답하지 않은 건 그냥 넘어가도 돼요.",
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
            question(step, "어떤 분야에 관심 있어요?", "여러 개 골라도 돼요.") { fieldChips }
        case .subfields(let id):
            let parent = model.tags.first { $0.id == id }
            question(step, "\(parent?.name ?? "고른 분야") 중에서는 어떤 분야요?", "여러 개 골라도 돼요.") {
                if let parent { subfieldSection(parent, titled: false) }
            }
        case .allSubfields:
            question(step, "분야별로 더 골라 볼까요?", "고르지 않으면 그 분야 전부를 찾아요.") {
                ForEach(pickedParents) { parent in subfieldSection(parent, titled: true) }
            }
        case .categories:
            question(step, "어떤 활동을 찾고 있어요?", "여러 개 골라도 돼요.") {
                Section { ForEach(Self.categories, id: \.code) { item in check(item.label, item.code.rawValue, in: \.categories) } }
            }
        case .traits:
            question(step, "활동하면서 무엇을 얻고 싶어요?", "고르지 않아도 괜찮아요.") {
                Section { ForEach(model.tags.filter { !$0.isField }) { tag in check(tag.name, tag.id, in: \.traitTagIds) } }
            }
        case .result:
            result
        }
    }
    private func question(_ step: Step, _ title: String, _ message: String, @ViewBuilder rows: () -> some View) -> some View {
        let questions = questions
        let position = (questions.firstIndex(of: step) ?? 0) + 1
        let next = questions.drop { $0 != step }.dropFirst().first ?? .result
        return DearbyQuestionPage(progress: Double(position) / Double(questions.count + 1), title: title, message: message, button: "다음",
                                  next: { go(next) }, content: rows)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("건너뛰기") { go(next) } } }
    }
    /// The questions in order for what is picked now; the progress bar follows their count.
    private var questions: [Step] {
        let parents = pickedParents
        let sub: [Step] = parents.count >= 3 ? [.allSubfields] : parents.map { .subfields($0.id) }
        return [.fields] + sub + [.categories, .traits]
    }
    /// Entering the result records the answers (`finish`) and loads their recommendations.
    private func go(_ step: Step) {
        if step == .result { model.finish(answers) }
        path.append(step)
    }
    // MARK: 분야

    private var fields: [TagOption] { model.tags.filter(\.isField) }
    private func children(of parent: TagOption) -> [TagOption] { fields.filter { $0.parentID == parent.id } }
    /// The first question's choices: 상위 분야 with 하위 분야 first, then those without a 상위 (server order within each).
    private var topFields: [TagOption] {
        let fields = fields
        let tops = fields.filter { tag in tag.parentID == nil || !fields.contains { $0.id == tag.parentID } }
        let parents = tops.filter { top in fields.contains { $0.parentID == top.id } }
        return parents + tops.filter { !parents.contains($0) }
    }
    /// Picked: its own id (for a 상위, `전부 좋아요`) or any of its 하위 ids is in the answers.
    private func isPicked(_ top: TagOption) -> Bool {
        answers.fieldTagIds.contains(top.id) || children(of: top).contains { answers.fieldTagIds.contains($0.id) }
    }
    private var pickedParents: [TagOption] { topFields.filter { isPicked($0) && !children(of: $0).isEmpty } }
    private func setPicked(_ top: TagOption, _ on: Bool) {
        let ids = Set([top.id] + children(of: top).map(\.id))
        answers.fieldTagIds.removeAll { ids.contains($0) }
        if on { answers.fieldTagIds.append(top.id) }
    }
    @ViewBuilder private var fieldChips: some View {
        let tops = topFields
        Section {
            if tops.isEmpty {
                Text(model.tags.isEmpty ? "분야를 불러오고 있어요" : "고를 수 있는 분야가 아직 없어요").foregroundStyle(.secondary)
            } else {
                ChipFlow(spacing: 8) {
                    ForEach(tops) { top in
                        DearbyToggleChip(title: top.name, systemImage: DearbyTagSymbol.name(top.nameEn),
                                         isOn: Binding(get: { isPicked(top) }, set: { setPicked(top, $0) }))
                    }
                }
                .padding(.vertical, 8).listRowBackground(Color.clear).listRowInsets(EdgeInsets(top: 0, leading: 20, bottom: 0, trailing: 20))
            }
        }
    }
    /// One 상위 분야's 하위 question: `{상위} 전부 좋아요` (the 상위 id; on until a 하위 is picked, and back on when the last one is unpicked) and its 하위 분야.
    private func subfieldSection(_ parent: TagOption, titled: Bool) -> some View {
        let children = children(of: parent)
        let childIDs = Set(children.map(\.id))
        let all = Binding(get: { answers.fieldTagIds.contains(parent.id) }, set: { on in
            guard on else { return }
            answers.fieldTagIds.removeAll { childIDs.contains($0) }
            answers.fieldTagIds.append(parent.id)
        })
        return Section {
            DearbyCheckRow(isOn: all) { Label("\(parent.name) 전부 좋아요", systemImage: DearbyTagSymbol.name(parent.nameEn)) }
            ForEach(children) { child in
                DearbyCheckRow(isOn: Binding(get: { answers.fieldTagIds.contains(child.id) }, set: { on in
                    answers.fieldTagIds.removeAll { $0 == child.id || $0 == parent.id }
                    if on { answers.fieldTagIds.append(child.id) }
                    if !answers.fieldTagIds.contains(where: childIDs.contains) { answers.fieldTagIds.append(parent.id) }
                })) { Label(child.name, systemImage: DearbyTagSymbol.name(child.nameEn)) }
            }
        } header: {
            if titled { Text(parent.name) }
        }
    }
    // MARK: 활동 종류·성격

    private func check<Value: Hashable>(_ label: String, _ value: Value,
                                        in list: WritableKeyPath<AccountClient.RecommendationPreferences, [Value]>) -> some View {
        DearbyCheckRow(isOn: Binding(get: { answers[keyPath: list].contains(value) }, set: { on in
            if on { answers[keyPath: list].append(value) } else { answers[keyPath: list].removeAll { $0 == value } }
        })) { Text(label) }
    }
    private var result: some View {
        let count = model.total
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
