import SwiftUI

/// One funnel step's picker: search the registered 활동 정보 (organizations, programs, activities or roles) or type
/// a name. A typed name is marked 직접 입력 and waits for admin approval before the history can go on a card.
struct ActivityInfoPickerView: View {
    let title: String
    let prompt: String
    @State private var search: ActivityInfoSearchModel
    @Binding var picked: ActivityInfoRef?
    @Binding var typed: String
    /// Called with the full search hit after a pick, for steps that derive other fields from it (an activity's organization and program).
    var onPick: ((ActivityInfoItem) -> Void)?
    init(title: String, prompt: String, kind: AccountClient.ActivityInfoKind, parentID: Int?, account: AccountViewModel,
         picked: Binding<ActivityInfoRef?>, typed: Binding<String>, onPick: ((ActivityInfoItem) -> Void)? = nil) {
        self.title = title
        self.prompt = prompt
        _search = State(initialValue: ActivityInfoSearchModel(kind: kind, parentID: parentID, account: account))
        _picked = picked
        _typed = typed
        self.onPick = onPick
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
            if let picked {
                HStack {
                    Label(picked.name, systemImage: "checkmark.circle.fill").font(.dearby(.headline)).foregroundStyle(DearbyStyle.teal)
                    Spacer()
                    Button("바꾸기") { self.picked = nil }.font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                }.padding(14).background(DearbyStyle.mint, in: RoundedRectangle(cornerRadius: 12))
            } else {
                DearbySearchField(prompt: prompt, text: $search.query, identifier: "activity-info-search")
                if !typed.isEmpty {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(typed).font(.dearby(.headline))
                            Text("직접 입력 · 관리자 승인 뒤 명함에 넣을 수 있어요").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                        }
                        Spacer()
                        Button("지우기") { typed = "" }.font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                    }.padding(14).overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
                }
                results
            }
        }
    }
    @ViewBuilder private var results: some View {
        let query = search.query.trimmingCharacters(in: .whitespacesAndNewlines)
        if search.loading && search.items.isEmpty {
            ProgressView().frame(maxWidth: .infinity, minHeight: 80).tint(DearbyStyle.teal)
        } else if search.failed {
            Text("목록을 불러오지 못했어요. 직접 적어도 돼요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
        }
        VStack(spacing: 0) {
            ForEach(search.items) { item in
                Button {
                    picked = item.ref
                    typed = ""
                    onPick?(item)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.name).font(.dearby(.body)).foregroundStyle(DearbyStyle.ink)
                        // Where the hit belongs: activities show 조직 · 프로그램, programs their organization, others the English name.
                        let parents = [item.organization?.name, item.program?.name].compactMap { $0 }
                        if let parent = parents.isEmpty ? item.nameEn : parents.joined(separator: " · ") {
                            Text(parent).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                        }
                    }.frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                }.buttonStyle(.plain)
                Divider()
            }
            if !query.isEmpty && !search.items.contains(where: { $0.name == query }) {
                Button {
                    typed = query
                    picked = nil
                    search.query = ""
                } label: {
                    Label("‘\(query)’ 직접 입력", systemImage: "pencil").font(.dearby(.body).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                        .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                }.buttonStyle(.plain).accessibilityHint("목록에 없으면 적은 이름 그대로 저장해요")
            } else if query.isEmpty && search.items.isEmpty && !search.loading {
                Text("이름으로 검색하거나 없으면 그대로 적어 주세요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).padding(.vertical, 12)
            }
        }
    }
}
