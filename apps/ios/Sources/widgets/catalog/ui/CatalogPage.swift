import SwiftUI

struct CatalogPage: View {
    let state: CatalogState
    let saved: Bool
    @State private var message: String?
    private let teal = Color(red: 0, green: 0.36, blue: 0.34)
    var body: some View {
        TimelineView(.explicit(state.expirationDates)) { timeline in
            List {
                connection
                if saved { savedSections(at: timeline.date) } else { discovery(at: timeline.date) }
            }
            .navigationDestination(for: String.self) { id in
                ActivityDetailView(state: state, activityID: id)
            }
        }
        .listStyle(.insetGrouped).scrollContentBackground(.hidden).background(.white)
        .tint(teal).navigationTitle(saved ? "저장" : "발견")
        .refreshable { await state.refresh() }
        .task { if state.catalog == nil && !state.loading { await state.refresh() } }
        .alert("저장하지 못했어요", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("확인") { message = nil }
        } message: { Text(message ?? "") }
    }
    @ViewBuilder private var connection: some View {
        if state.loading { ProgressView("활동을 불러오는 중…") }
        if let error = state.error {
            Section {
                Label(error, systemImage: "exclamationmark.arrow.trianglehead.2.clockwise.rotate.90")
                    .font(.footnote)
                Button("다시 불러오기") { Task { await state.refresh() } }.disabled(state.loading)
            }
        }
        if state.fromCache {
            Text("기기에 보관한 정보 · 만료된 활동은 발견에 표시하지 않아요.").font(.footnote).foregroundStyle(.secondary)
        }
        if let fetched = state.fetchedAt {
            Text("마지막 수신 \(fetched.formatted(date: .abbreviated, time: .shortened))")
                .font(.caption).foregroundStyle(.secondary)
        }
    }
    @ViewBuilder private func discovery(at now: Date) -> some View {
        if let catalog = state.catalog {
            let activities = catalog.activities.filter { $0.isOpen(at: now) }
            if activities.isEmpty {
                ContentUnavailableView("확인된 모집 중 활동이 없어요", systemImage: "safari",
                    description: Text(state.fromCache ? "보관한 정보만으로 현재 모집을 확인할 수 없어요. 다시 불러와 주세요."
                        : "공식 출처에서 모집 여부와 최신성이 확인된 활동이 표시됩니다."))
            } else {
                Section("모집 중 활동") { ForEach(activities) { row($0, at: now) } }
            }
        } else if !state.loading && state.error == nil {
            Button("활동 불러오기") { Task { await state.refresh() } }
        }
    }
    @ViewBuilder private func savedSections(at now: Date) -> some View {
        Section {
            Text("프로그램과 조직을 이 기기에 저장해요. 계정 동기화나 모집 알림은 제공하지 않아요.")
                .font(.footnote).foregroundStyle(.secondary)
        }
        if state.local.programIDs.isEmpty && state.local.organizationIDs.isEmpty {
            ContentUnavailableView("저장한 활동", systemImage: "bookmark",
                description: Text("활동 상세에서 관심 있는 프로그램과 조직을 저장해 보세요."))
        }
        ForEach(state.local.programIDs.sorted(), id: \.self) { id in
            Section {
                let activities = state.catalog?.activities.filter { $0.programId == id } ?? []
                savedRows(activities, at: now)
                Button("프로그램 저장 해제") { mutate { try state.toggleProgram(id) } }
            } header: { Text(state.catalog?.programs.first { $0.id == id }?.title ?? "저장한 프로그램 · 정보 미수신") }
        }
        ForEach(state.local.organizationIDs.sorted(), id: \.self) { id in
            Section {
                let activities = state.catalog?.activities.filter { $0.organizationId == id } ?? []
                savedRows(activities, at: now)
                Button("조직 저장 해제") { mutate { try state.toggleOrganization(id) } }
            } header: { Text(state.catalog?.organizations.first { $0.id == id }?.name ?? "저장한 조직 · 정보 미수신") }
        }
    }
    @ViewBuilder private func savedRows(_ activities: [ActivityModel], at now: Date) -> some View {
        if activities.isEmpty { Text("표시할 활동 정보가 없어요. 저장은 유지됩니다.").font(.footnote) }
        ForEach(activities) { row($0, at: now) }
    }
    private func row(_ activity: ActivityModel, at now: Date) -> some View {
        NavigationLink(value: activity.id) {
            VStack(alignment: .leading, spacing: 8) {
                Text(activity.status(at: now)).font(.caption.bold()).foregroundStyle(teal)
                Text(activity.title).font(.headline).foregroundStyle(.primary)
                Text(activity.summary).font(.subheadline).foregroundStyle(.secondary).lineLimit(3)
                Text(activity.dateLabel.isEmpty ? "일정 미확인" : activity.dateLabel).font(.caption)
                Text(activity.participationType == .selection ? "선발형 활동" : "참가등록형 행사").font(.caption)
                Text("출처 확인: \(ActivityText.date(activity.sourceCheckedAt))").font(.caption2).foregroundStyle(.secondary)
            }.padding(.vertical, 8)
        }.accessibilityIdentifier("activity-\(activity.id)")
    }
    private func mutate(_ action: () throws -> Void) {
        do { try action() } catch { message = error.localizedDescription }
    }
}
