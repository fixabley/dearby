import SwiftUI

/// 발견 필터 (contract "필터 시트" 2026-10-26, user "발견 필터를 `필터` 버튼 하나로 묶는다"): the list's first row — a `필터` button (the applied
/// count beside it, `필터 2`) that opens `DiscoveryFilterSheet`, then the applied filters as chips, each × removing that one. The default
/// (`지원 가능` alone, every 종류) shows no chip.
struct DiscoveryFilterBar: View {
    let kind: DiscoveryKind
    let groups: Set<StatusGroup>
    let categories: Set<ActivityCategory>
    let apply: (Set<StatusGroup>, Set<ActivityCategory>) -> Void
    @State private var filtering = false
    /// The chips in order: the chosen 묶음 (none when only `지원 가능`), then the chosen 종류 in contract order.
    private var chosenGroups: [StatusGroup] { groups == [.available] ? [] : StatusGroup.allCases.filter(groups.contains) }
    private var chosenCategories: [ActivityCategory] { ActivityCategory.allCases.filter(categories.contains) }
    var body: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                Button { filtering = true } label: {
                    let count = chosenGroups.count + chosenCategories.count
                    Label(count > 0 ? "필터 \(count)" : "필터", systemImage: "line.3.horizontal.decrease.circle")
                }
                .accessibilityIdentifier("discovery-filter")
                ForEach(chosenGroups, id: \.self) { group in
                    chip(group.title, systemImage: nil) { apply(groups.subtracting([group]), categories) }
                }
                ForEach(chosenCategories, id: \.self) { category in
                    chip(category.label, systemImage: category.systemImage) { apply(groups, categories.subtracting([category])) }
                }
            }
            .buttonStyle(.bordered).buttonBorderShape(.capsule).font(.dearby(.subheadline).weight(.semibold))
        }
        .scrollIndicators(.hidden).scrollClipDisabled()
        .sheet(isPresented: $filtering) {
            DiscoveryFilterSheet(kind: kind, groups: groups, categories: categories, apply: apply)
                .presentationDetents([.medium, .large])
        }
    }
    /// One applied filter: its name and × (the whole chip removes it).
    private func chip(_ title: String, systemImage: String?, remove: @escaping () -> Void) -> some View {
        Button(action: remove) {
            HStack(spacing: 4) {
                if let systemImage { Label(title, systemImage: systemImage) } else { Text(title) }
                Image(systemName: "xmark").font(.dearby(.caption).weight(.semibold))
            }
        }
        .tint(DearbyStyle.teal).accessibilityLabel("\(title) 필터 지우기")
    }
}

/// The 필터 sheet: a system inset-grouped list with one checkmark row per 묶음 (`활동 상태`) and, on the 프로그램 side, per 종류 (`종류`;
/// the 이벤트 side offers none — `DiscoveryKind.categories`). The draft lives here and reaches the list only through `적용`; `초기화` puts the
/// draft back to the default (`지원 가능`, every 종류) without applying. Closing keeps the list as it was.
struct DiscoveryFilterSheet: View {
    let kind: DiscoveryKind
    @State private var groups: Set<StatusGroup>
    @State private var categories: Set<ActivityCategory>
    let apply: (Set<StatusGroup>, Set<ActivityCategory>) -> Void
    @Environment(\.dismiss) private var dismiss
    init(kind: DiscoveryKind, groups: Set<StatusGroup>, categories: Set<ActivityCategory>, apply: @escaping (Set<StatusGroup>, Set<ActivityCategory>) -> Void) {
        self.kind = kind
        _groups = State(initialValue: groups)
        _categories = State(initialValue: categories)
        self.apply = apply
    }
    var body: some View {
        NavigationStack {
            List {
                Section("활동 상태") {
                    ForEach(StatusGroup.allCases, id: \.self) { group in
                        row(group.title, systemImage: nil, on: groups.contains(group), identifier: "discovery-filter-status-\(group.rawValue)") {
                            groups.formSymmetricDifference([group])
                        }
                    }
                }
                if !kind.categories.isEmpty {
                    Section("종류") {
                        ForEach(kind.categories, id: \.self) { category in
                            row(category.label, systemImage: category.systemImage, on: categories.contains(category),
                                identifier: "discovery-filter-category-\(category.rawValue.lowercased())") {
                                categories.formSymmetricDifference([category])
                            }
                        }
                    }
                }
            }
            .font(.dearby(.body))
            .navigationTitle("필터").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
            .safeAreaInset(edge: .bottom) {
                HStack(spacing: 12) {
                    Button("초기화") {
                        groups = [.available]
                        categories = []
                    }
                    .buttonStyle(.bordered).accessibilityIdentifier("discovery-filter-reset")
                    Button("적용") {
                        apply(groups, categories)
                        dismiss()
                    }
                    .buttonStyle(.borderedProminent).tint(DearbyStyle.tealFill).accessibilityIdentifier("discovery-filter-apply")
                }
                .controlSize(.large).font(.dearby(.body).weight(.semibold))
                .frame(maxWidth: .infinity).padding(.horizontal, 20).padding(.vertical, 12).background(.bar)
            }
        }
    }
    /// A multi-select row (contract "토글 라벨은 바로 옆": checkmark rows, not switches): name, and `checkmark.circle.fill` or `circle` at the end.
    private func row(_ title: String, systemImage: String?, on: Bool, identifier: String, toggle: @escaping () -> Void) -> some View {
        Button(action: toggle) {
            HStack {
                if let systemImage { Label(title, systemImage: systemImage) } else { Text(title) }
                Spacer(minLength: 12)
                Image(systemName: on ? "checkmark.circle.fill" : "circle").foregroundStyle(on ? DearbyStyle.teal : .secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
        }
        .buttonStyle(.plain).foregroundStyle(.primary)
        .accessibilityAddTraits(on ? .isSelected : []).accessibilityIdentifier(identifier)
    }
}
