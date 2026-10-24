import SwiftUI

/// 지원 자격 정보 동의 시트 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24, 토스처럼 입력하는 곳에서 동의): 아래에서 올라오는 시트에 제목,
/// 요약 네 줄, `[필수] 지원 자격 정보 수집·이용 동의`(누르면 전문), `동의하고 입력하기`, `닫기`. 동의는 값과 함께 PUT의 `consentVersion`으로 기록된다.
struct EligibilityConsentSheet: View {
    let webOrigin: URL
    let agree: () -> Void
    @State private var opening: URL?
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("지원할 수 있는 활동만 보여 드리려면 이 정보가 필요해요").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 10) {
                line("person.text.rectangle", "모으는 정보: 재학 상태, 학년, 태어난 해")
                line("line.3.horizontal.decrease.circle", "쓰는 곳: 지원 자격 거르기와 활동 추천")
                line("lock", "보관: 내 계정에만, 다른 사람에게 보이지 않아요")
                line("trash", "언제든 프로필에서 지우면 바로 삭제돼요")
            }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemBackground), in: .rect(cornerRadius: 14, style: .continuous))
            Button { opening = webOrigin.appending(path: "privacy") } label: {
                HStack {
                    Text("[필수] 지원 자격 정보 수집·이용 동의").foregroundStyle(.primary)
                    Spacer(minLength: 8)
                    Text("전문 보기").font(.dearby(.footnote)).foregroundStyle(.secondary)
                    Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
                }.frame(minHeight: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain).accessibilityHint("전문을 보여요")
            Spacer(minLength: 0)
            VStack(spacing: 8) {
                Button { agree() } label: { Text("동의하고 입력하기").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).tint(DearbyStyle.tealFill).accessibilityIdentifier("eligibility-agree")
                Button { dismiss() } label: { Text("닫기").frame(maxWidth: .infinity, minHeight: 44) }.buttonStyle(.borderless)
            }.controlSize(.large).font(.dearby(.body).weight(.semibold))
        }
        .font(.dearby(.body))
        .padding(.horizontal, 24).padding(.top, 28).padding(.bottom, 12)
        .sheet(item: $opening) { url in SafariView(url: url).ignoresSafeArea() }
    }
    private func line(_ symbol: String, _ text: String) -> some View {
        Label { Text(text).font(.dearby(.subheadline)) } icon: { Image(systemName: symbol).foregroundStyle(DearbyStyle.teal) }
    }
}

/// 지원 자격 정보 입력, 한 화면에 질문 하나: 상황 → 학년(재학·휴학·졸업 예정만) → 태어난 해(휠). 상황·학년은 고르면 다음 화면으로 넘어간다.
/// 마지막 `저장`이 PUT으로 값과 동의 버전을 함께 보낸다. 고치기는 저장된 값을 고른 채 시작한다.
struct EligibilityFlow: View {
    let model: EligibilityModel
    @State private var path: [Step] = []
    @State private var status: EligibilityStatus?
    @State private var schoolYear: Int?
    @State private var birthYear: Int
    @State private var saving = false
    @State private var error: String?
    @Environment(\.dismiss) private var dismiss
    enum Step: Hashable { case grade, birthYear }
    private static let thisYear = Calendar(identifier: .gregorian).component(.year, from: Date())
    init(model: EligibilityModel) {
        self.model = model
        let saved = model.profile
        _status = State(initialValue: saved.flatMap(EligibilityStatus.init))
        _schoolYear = State(initialValue: saved?.schoolYear)
        _birthYear = State(initialValue: saved?.birthYear ?? Self.thisYear - 22)
    }
    var body: some View {
        NavigationStack(path: $path) {
            DearbyQuestionPage(progress: 1 / 3, title: "지금 어떤 상황이에요?", button: "다음", disabled: status == nil, next: afterStatus) {
                Section { ForEach(EligibilityStatus.allCases, id: \.self) { choice in pick(choice.label, selected: status == choice) { status = choice; afterStatus() } } }
            }
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
            .navigationDestination(for: Step.self) { step in
                switch step {
                case .grade:
                    DearbyQuestionPage(progress: 2 / 3, title: "몇 학년이에요?", button: "다음", disabled: schoolYear == nil, next: { path.append(.birthYear) }) {
                        Section {
                            ForEach(1...5, id: \.self) { year in
                                pick(EligibilityStatus.gradeLabel(year), selected: schoolYear == year) { schoolYear = year; path.append(.birthYear) }
                            }
                        }
                    }
                case .birthYear:
                    DearbyQuestionPage(progress: 1, title: "태어난 해를 알려 주세요", message: "나이 제한이 있는 활동을 거를 때만 써요.",
                                       button: "저장", busy: saving, next: save) {
                        Section {
                            Picker("태어난 해", selection: $birthYear) {
                                ForEach((Self.thisYear - 80...Self.thisYear - 10).reversed(), id: \.self) { Text("\(String($0))년").tag($0) }
                            }.pickerStyle(.wheel)
                        } footer: {
                            if let error { DearbyFieldError(text: error) }
                        }
                    }
                }
            }
        }
        .tint(DearbyStyle.teal)
    }
    private func afterStatus() {
        guard let status else { return }
        path.append(status.hasGrade ? .grade : .birthYear)
    }
    private func pick(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label).foregroundStyle(.primary)
                Spacer()
                if selected { Image(systemName: "checkmark").foregroundStyle(DearbyStyle.teal).fontWeight(.semibold) }
            }.frame(minHeight: 44).contentShape(Rectangle())
        }
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
    private func save() {
        guard let status else { return }
        saving = true
        Task {
            error = await model.save(status: status, schoolYear: schoolYear, birthYear: birthYear)
            saving = false
            if error == nil { dismiss() }
        }
    }
}
