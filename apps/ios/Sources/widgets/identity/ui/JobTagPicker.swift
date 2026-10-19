import SwiftUI

/// 직무 tag picker (contract "직무"): form rows for the page's `Section` — chips for the chosen jobs, a search over the job
/// tree (`GET /v1/jobs`) and its hits as rows; a name that is not in the tree can be typed and waits for admin approval
/// before it reaches a card. The page puts `note(for:)` in its section footer.
/// The search behaves like discovery (contract "퍼널 검색은 발견 검색과 같은 경험" 2026-10-20): `DearbySearchField`, live results, whole-row hits,
/// a `CenteredMessage` when nothing matches and a `‘검색어’ 직접 입력` row at the bottom. `focus` is the page's search focus so its keyboard 완료 can close it.
struct JobTagPicker: View {
    @Binding var form: ProfileForm
    var focus: FocusState<Bool>.Binding?
    @State private var search: ActivityInfoSearchModel
    init(form: Binding<ProfileForm>, account: AccountViewModel, focus: FocusState<Bool>.Binding? = nil) {
        _form = form
        self.focus = focus
        _search = State(initialValue: ActivityInfoSearchModel(kind: .jobs, parentID: nil, account: account))
    }
    private var full: Bool { form.jobs.count >= ProfileForm.maxJobs }
    var body: some View {
        if !form.jobs.isEmpty {
            ChipFlow(spacing: 8) {
                ForEach(form.jobs) { job in chip(job) }
            }.padding(.vertical, 4)
        }
        if !full {
            DearbySearchField(prompt: "직무 검색 (예: 백엔드, 디자인)", text: $search.query, identifier: "job-search", focus: focus)
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear).listRowSeparator(.hidden)
            results
        }
    }
    /// Footer text for the section holding the picker: the limit once it is reached, and the approval note for typed jobs.
    static func note(for form: ProfileForm) -> String? {
        var notes: [String] = []
        if form.jobs.count >= ProfileForm.maxJobs { notes.append("직무는 \(ProfileForm.maxJobs)개까지 고를 수 있어요.") }
        if form.jobs.contains(where: { $0.isCustom && $0.approval == "pending" }) { notes.append("직접 적은 직무는 관리자 승인 뒤 명함에 들어가요.") }
        return notes.isEmpty ? nil : notes.joined(separator: " ")
    }
    private func chip(_ job: ProfileJob) -> some View {
        Button { form.removeJob(job.id) } label: {
            HStack(spacing: 6) {
                if job.isCustom && job.approval == "pending" { Image(systemName: "clock").font(.dearby(.footnote)) }
                Text(job.title).fontWeight(.semibold)
                Image(systemName: "xmark").font(.dearby(.caption2).weight(.bold))
            }
            .foregroundStyle(job.approval == "rejected" ? DearbyStyle.danger : DearbyStyle.teal)
            .padding(.horizontal, 14).frame(minHeight: 44)
            .background(job.approval == "rejected" ? DearbyStyle.muted : DearbyStyle.mint, in: Capsule())
        }.buttonStyle(.plain).accessibilityLabel("\(job.title) 직무\(job.isCustom && job.approval == "pending" ? ", 승인 대기" : "")").accessibilityHint("눌러서 지워요")
    }
    // Results show only for a typed query (as on Android), so the editor is not a permanent list under the chips.
    @ViewBuilder private var results: some View {
        let query = search.query.trimmingCharacters(in: .whitespacesAndNewlines)
        if query.isEmpty {
            Text("이름으로 검색해 여러 개 고르거나, 없으면 그대로 적어 주세요.").font(.dearby(.subheadline)).foregroundStyle(.secondary)
                .listRowBackground(Color.clear)
        } else if search.loading && search.items.isEmpty {
            ProgressView().frame(maxWidth: .infinity).listRowBackground(Color.clear)
        } else if search.failed {
            Text("목록을 불러오지 못했어요. 직접 적어도 돼요.").font(.dearby(.subheadline)).foregroundStyle(.secondary).listRowBackground(Color.clear)
        } else if search.items.isEmpty {
            CenteredMessage(symbol: "magnifyingglass", title: "찾는 직무가 없어요", message: "다른 이름으로 검색하거나 아래에서 그대로 적어요.", embedded: true)
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear).listRowSeparator(.hidden)
        }
        if !query.isEmpty {
            ForEach(search.items) { item in
                Button { form.addJob(picked: item.ref) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.name).foregroundStyle(DearbyStyle.ink)
                        if let parent = item.parent?.name ?? item.nameEn { Text(parent).font(.dearby(.footnote)).foregroundStyle(.secondary) }
                    }.frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                }
            }
            if !search.items.contains(where: { $0.name == query }) {
                Button {
                    form.addJob(picked: nil, typed: query)
                    search.query = ""
                } label: {
                    Label("‘\(query)’ 직접 입력", systemImage: "pencil").fontWeight(.semibold)
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                }.accessibilityHint("목록에 없으면 적은 이름 그대로 저장해요")
            }
        }
    }
}
