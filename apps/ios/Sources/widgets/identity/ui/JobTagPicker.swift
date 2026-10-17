import SwiftUI

/// 직무 tag picker (contract "직무"): chips for the chosen jobs, then a search over the job tree (`GET /v1/jobs`); a
/// name that is not in the tree can be typed and waits for admin approval before it reaches a card.
struct JobTagPicker: View {
    @Binding var form: ProfileForm
    @State private var search: ActivityInfoSearchModel
    init(form: Binding<ProfileForm>, account: AccountViewModel) {
        _form = form
        _search = State(initialValue: ActivityInfoSearchModel(kind: .jobs, parentID: nil, account: account))
    }
    private var full: Bool { form.jobs.count >= ProfileForm.maxJobs }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !form.jobs.isEmpty {
                ChipFlow(spacing: 8) {
                    ForEach(form.jobs) { job in chip(job) }
                }
            }
            if form.jobs.contains(where: { $0.isCustom && $0.approval == "pending" }) {
                Text("직접 적은 직무는 관리자 승인 뒤 명함에 들어가요.").font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
            }
            if full {
                Text("직무는 \(ProfileForm.maxJobs)개까지 고를 수 있어요.").font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
            } else {
                DearbySearchField(prompt: "직무 검색 (예: 백엔드, 디자인)", text: $search.query, identifier: "job-search")
                results
            }
        }
    }
    private func chip(_ job: ProfileJob) -> some View {
        Button { form.removeJob(job.id) } label: {
            HStack(spacing: 6) {
                if job.isCustom && job.approval == "pending" { Image(systemName: "clock").font(.dearby(.footnote)) }
                Text(job.title).font(.dearby(.body).weight(.semibold))
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
            Text("이름으로 검색해 여러 개 고르거나, 없으면 그대로 적어 주세요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).padding(.vertical, 8)
        } else if search.loading && search.items.isEmpty {
            ProgressView().frame(maxWidth: .infinity, minHeight: 56).tint(DearbyStyle.teal)
        } else if search.failed {
            Text("목록을 불러오지 못했어요. 직접 적어도 돼요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
        }
        if !query.isEmpty { VStack(spacing: 0) {
            ForEach(search.items) { item in
                Button {
                    form.addJob(picked: item.ref)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.name).font(.dearby(.title3)).foregroundStyle(DearbyStyle.ink)
                        if let parent = item.parent?.name ?? item.nameEn { Text(parent).font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet) }
                    }.frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                }.buttonStyle(.plain)
                Divider()
            }
            if !query.isEmpty && !search.items.contains(where: { $0.name == query }) {
                Button {
                    form.addJob(picked: nil, typed: query)
                    search.query = ""
                } label: {
                    Label("‘\(query)’ 직접 입력", systemImage: "pencil").font(.dearby(.title3).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                }.buttonStyle(.plain).accessibilityHint("목록에 없으면 적은 이름 그대로 저장해요")
            }
        } }
    }
}

/// Wraps chips onto new lines (iOS 16 `Layout`).
struct ChipFlow: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width { x = 0; y += rowHeight + spacing; rowHeight = 0 }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: width, height: y + rowHeight)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX { x = bounds.minX; y += rowHeight + spacing; rowHeight = 0 }
            view.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
