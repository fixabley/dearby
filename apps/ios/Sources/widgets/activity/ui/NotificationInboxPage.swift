import SwiftUI

/// 알림 목록 (contract "알림 목록(발견 우상단 종)" 2026-10-25), pushed by a tab's bell; it looks like the discovery program list. Notifications are
/// grouped in program cards (the discovery header with the unread count in the badge's place), newest first under `이번 주` (last 7 days) and
/// `지난 알림`. Only a tap expands a card; its rows read like activity cards: a teal dot while unread, the activity name in bold, the kind chip
/// and when it arrived (`오늘 08:00`·`어제`·`10월 21일`). A row opens the activity detail and marks it read. `모두 읽음` at the top right.
struct NotificationInboxPage: View {
    let model: InboxModel
    let state: ActivityViewModel
    @State private var opened: String?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        let now = Date()
        let week = Calendar.current.date(byAdding: .day, value: -7, to: now) ?? now
        let recent = model.groups.filter { ($0.latest ?? .distantPast) >= week }
        let older = model.groups.filter { ($0.latest ?? .distantPast) < week }
        Group {
            switch model.phase {
            case .loading, .signedOut:
                ScrollView { DearbyRowsSkeleton(count: 5).padding(20) }.scrollDisabled(true)
            case .failed:
                CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "알림을 불러오지 못했어요", message: "연결을 확인한 뒤 다시 시도해 주세요.",
                                primary: MessageAction("다시 시도") { Task { await model.load() } })
            case .loaded where model.groups.isEmpty:
                CenteredMessage(symbol: "bell", title: "아직 알림이 없어요", message: "즐겨찾기한 프로그램·활동의 소식이 여기에 모여요.")
            case .loaded:
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        section("이번 주", recent, now: now)
                        section("지난 알림", older, now: now)
                        if model.hasMore {
                            ProgressView().frame(maxWidth: .infinity, minHeight: 44).task { await model.loadMore() }
                        }
                    }
                    .padding(.horizontal, 20).padding(.vertical, 8)
                }
                .refreshable { await model.load() }
            }
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("알림").navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("모두 읽음") { model.readAll() }.disabled(model.unread == 0 && !model.groups.contains { $0.unread > 0 })
                    .accessibilityIdentifier("inbox-read-all")
            }
        }
        .navigationDestination(item: $opened) { ActivityDetailView(state: state, activityID: $0, source: "inbox") }
        .task { await model.load() }
    }
    @ViewBuilder private func section(_ title: String, _ groups: [InboxGroup], now: Date) -> some View {
        if !groups.isEmpty {
            Text(title).font(.dearby(.headline)).accessibilityAddTraits(.isHeader).padding(.top, 8).padding(.horizontal, 4)
            ForEach(groups) { card($0, now: now) }
        }
    }
    private func card(_ group: InboxGroup, now: Date) -> some View {
        let program = group.program, open = model.expanded.contains(group.id)
        return VStack(alignment: .leading, spacing: 0) {
            ProgramHeader(name: program.name, organization: program.organization, category: program.category, logoURL: program.logoURL,
                          openCount: 0, nearestDeadline: nil,
                          fieldTags: program.fieldTags, traitTags: program.traitTags, unreadCount: group.unread, expanded: open) {
                EmptyView()
            } toggle: {
                withAnimation(reduceMotion ? nil : .spring(response: 0.35, dampingFraction: 0.85)) { model.toggle(group.id) }
            }
            if open {
                VStack(spacing: 8) { ForEach(group.items) { row($0, now: now) } }
                    .padding(EdgeInsets(top: 0, leading: 10, bottom: 12, trailing: 10))
                    .transition(reduceMotion ? .identity : .opacity.combined(with: .move(edge: .top)))
            }
        }
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
        .clipShape(.rect(cornerRadius: 20, style: .continuous))
    }
    private func row(_ item: InboxItem, now: Date) -> some View {
        let urgent = item.kind == .deadline || item.kind == .deadlineEve
        return Button {
            model.read(item.id)
            Task { if await state.prepare(item.activityID) { opened = item.activityID } }
        } label: {
            HStack(alignment: .top, spacing: 10) {
                Circle().fill(item.read ? Color.clear : DearbyStyle.teal).frame(width: 8, height: 8).padding(.top, 6)
                VStack(alignment: .leading, spacing: 6) {
                    Text(item.activityName).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(.primary).multilineTextAlignment(.leading)
                    HStack(spacing: 8) {
                        Text(item.kind?.label ?? "알림").font(.dearby(.caption2).weight(.semibold))
                            .foregroundStyle(urgent ? DearbyStyle.danger : DearbyStyle.teal)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(urgent ? DearbyStyle.danger.opacity(0.12) : DearbyStyle.mint, in: Capsule())
                        if let createdAt = item.createdAt { Text(Self.received(createdAt, now: now)).font(.dearby(.footnote)).foregroundStyle(.secondary) }
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary).padding(.top, 4)
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemGroupedBackground), in: .rect(cornerRadius: 14, style: .continuous)).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([item.read ? nil : "읽지 않음", item.activityName, item.kind?.label ?? "알림",
                             item.createdAt.map { Self.received($0, now: now) }].compactMap { $0 }.joined(separator: ", "))
        .accessibilityHint("누르면 활동 상세를 보여요")
    }
    /// `오늘 08:00`, `어제`, else `10월 21일` (`2025년 10월 21일` in another year), in the device's zone (the server sends at 8:00 there).
    static func received(_ date: Date, now: Date) -> String {
        let calendar = Calendar.current
        if calendar.isDate(date, inSameDayAs: now) { return "오늘 " + ActivityText.format(date, "HH:mm", zone: .current) }
        if calendar.isDateInYesterday(date) { return "어제" }
        let sameYear = calendar.component(.year, from: date) == calendar.component(.year, from: now)
        return ActivityText.format(date, sameYear ? "M월 d일" : "yyyy년 M월 d일", zone: .current)
    }
}
