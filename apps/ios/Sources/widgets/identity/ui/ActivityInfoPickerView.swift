import SwiftUI

/// One funnel step's picker as a form `Section`: search the registered 활동 정보 (organizations, programs, activities or
/// roles) or type a name. A typed name is marked 직접 입력 and waits for admin approval before the history can go on a card.
/// `footer` carries the step's error text under the rows.
/// The search behaves like discovery (contract "퍼널 검색은 발견 검색과 같은 경험" 2026-10-20): `DearbySearchField`, live results, whole-row hits,
/// a `CenteredMessage` when nothing matches and a `‘검색어’ 직접 입력` row at the bottom. `focus` is the page's search focus so its keyboard 완료 can close it.
struct ActivityInfoPickerView: View {
    let title: String
    let prompt: String
    @State private var search: ActivityInfoSearchModel
    @Binding var picked: ActivityInfoRef?
    @Binding var typed: String
    var focus: FocusState<Bool>.Binding?
    var footer: String?
    /// Called with the full search hit after a pick, for steps that derive other fields from it (an activity's organization and program).
    var onPick: ((ActivityInfoItem) -> Void)?
    init(title: String, prompt: String, kind: AccountClient.ActivityInfoKind, parentID: Int?, account: AccountViewModel,
         picked: Binding<ActivityInfoRef?>, typed: Binding<String>, focus: FocusState<Bool>.Binding? = nil, footer: String? = nil,
         onPick: ((ActivityInfoItem) -> Void)? = nil) {
        self.title = title
        self.prompt = prompt
        _search = State(initialValue: ActivityInfoSearchModel(kind: kind, parentID: parentID, account: account))
        _picked = picked
        _typed = typed
        self.focus = focus
        self.footer = footer
        self.onPick = onPick
    }
    var body: some View {
        Section {
            if let picked {
                // The whole row is the 바꾸기 button (contract "터치 컴포넌트": 묶인 영역 전체가 터치 대상).
                Button { self.picked = nil } label: {
                    HStack(spacing: 12) {
                        Label(picked.name, systemImage: "checkmark.circle").fontWeight(.semibold).foregroundStyle(DearbyStyle.teal)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text("바꾸기").foregroundStyle(DearbyStyle.teal)
                    }.frame(minHeight: 44).contentShape(Rectangle())
                }.accessibilityLabel("\(picked.name), 고름").accessibilityHint("눌러서 바꿔요")
            } else {
                DearbySearchField(prompt: prompt, text: $search.query, identifier: "activity-info-search", focus: focus)
                    .listRowInsets(EdgeInsets()).listRowBackground(Color.clear).listRowSeparator(.hidden)
                if !typed.isEmpty {
                    Button { typed = "" } label: {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(typed).fontWeight(.semibold).foregroundStyle(DearbyStyle.ink)
                                Text("직접 입력 · 관리자 승인 뒤 명함에 넣을 수 있어요").font(.dearby(.footnote)).foregroundStyle(.secondary)
                            }.frame(maxWidth: .infinity, alignment: .leading)
                            Text("지우기").foregroundStyle(DearbyStyle.teal)
                        }.frame(minHeight: 44).contentShape(Rectangle())
                    }.accessibilityLabel("\(typed), 직접 입력").accessibilityHint("눌러서 지워요")
                }
                results
            }
        } header: {
            Text(title).font(.dearby(.title3).bold()).foregroundStyle(.primary)
        } footer: {
            if let footer { DearbyFieldError(text: footer) }
        }
    }
    @ViewBuilder private var results: some View {
        let query = search.query.trimmingCharacters(in: .whitespacesAndNewlines)
        if search.loading && search.items.isEmpty {
            DearbyRowsSkeleton()
        } else if search.failed {
            Text("목록을 불러오지 못했어요. 직접 적어도 돼요.").font(.dearby(.subheadline)).foregroundStyle(.secondary).listRowBackground(Color.clear)
        } else if search.items.isEmpty && !query.isEmpty {
            CenteredMessage(symbol: "magnifyingglass", title: "찾는 항목이 없어요", message: "다른 이름으로 검색하거나 아래에서 그대로 적어요.", embedded: true)
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear).listRowSeparator(.hidden)
        }
        ForEach(search.items) { item in
            Button {
                picked = item.ref
                typed = ""
                onPick?(item)
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name).foregroundStyle(DearbyStyle.ink)
                    // Where the hit belongs: activities show 조직 · 프로그램, programs their organization, others the English name.
                    let parents = [item.organization?.name, item.program?.name].compactMap { $0 }
                    if let parent = parents.isEmpty ? item.nameEn : parents.joined(separator: " · ") {
                        Text(parent).font(.dearby(.footnote)).foregroundStyle(.secondary)
                    }
                }.frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
            }
        }
        if !query.isEmpty && !search.items.contains(where: { $0.name == query }) {
            Button {
                typed = query
                picked = nil
                search.query = ""
            } label: {
                Label("‘\(query)’ 직접 입력", systemImage: "pencil").fontWeight(.semibold)
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
            }.accessibilityHint("목록에 없으면 적은 이름 그대로 저장해요")
        } else if query.isEmpty && search.items.isEmpty && !search.loading {
            Text("이름으로 검색하거나 없으면 그대로 적어 주세요.").font(.dearby(.subheadline)).foregroundStyle(.secondary).listRowBackground(Color.clear)
        }
    }
}
