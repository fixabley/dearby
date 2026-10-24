import SwiftUI

/// 오늘의 맞춤 대기열 (contract "맞춤 대기열(스토리형 쇼츠)" 2026-10-24): full-screen vertical pages (`.paging`), one activity each
/// (`QueueStoryCard`), then `오늘 대기열을 다 봤어요` with `더 받기`. `자세히` opens the activity detail in a sheet so the place in the queue
/// stays; `⋯` is the system confirmation dialog, and a choice shows a `되돌리기` notice for 4 seconds before it is recorded.
struct DiscoveryQueuePage: View {
    let model: DiscoveryQueueModel
    let state: ActivityViewModel
    /// Signed out, the 추천 answers the stand-in queue is built from.
    let preferences: AccountClient.RecommendationPreferences?
    @State private var position: String?
    @State private var detail: RecommendedActivity?
    @State private var choosing: RecommendedActivity?
    @State private var now = Date()
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private static let endID = "queue-end"
    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(Array(model.items.enumerated()), id: \.element.id) { index, item in
                        card(item, index: index, insets: proxy.safeAreaInsets).containerRelativeFrame([.horizontal, .vertical])
                    }
                    endPage.padding(proxy.safeAreaInsets).containerRelativeFrame([.horizontal, .vertical]).id(Self.endID)
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $position)
            .scrollIndicators(.hidden)
            .ignoresSafeArea()
        }
        .background(.black)
        // Dark controls on the covers; the detail sheet keeps the system appearance.
        .environment(\.colorScheme, .dark)
        .overlay(alignment: .topTrailing) {
            Button { dismiss() } label: {
                Image(systemName: "xmark").font(.body.weight(.semibold)).frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .foregroundStyle(.white).accessibilityLabel("닫기").padding(.trailing, 6).padding(.top, 10)
        }
        .overlay(alignment: .bottom) { notice.environment(\.colorScheme, .dark) }
        .task { await model.open(preferences) }
        .onDisappear { model.commitPending() }
        .confirmationDialog("이 활동", isPresented: Binding(get: { choosing != nil }, set: { if !$0 { choosing = nil } }),
                            titleVisibility: .hidden, presenting: choosing) { item in
            ForEach(DiscoveryQueueModel.Choice.allCases.filter { $0 != .lessField || !item.activity.fieldTags.isEmpty }) { choice in
                Button(choice.label) { choose(choice, item) }
            }
        }
        .sheet(item: $detail) { item in
            NavigationStack {
                ActivityDetailView(state: state, activityID: item.id)
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { detail = nil } } }
            }
            // The app root cannot present `신청하셨나요?` over this screen, so the detail asks it here (same sheet as the root's).
            .sheet(item: Binding(get: { state.asking }, set: { if $0 == nil { state.answer(.notYet) } })) { activity in
                ApplyConfirmationSheet(activityTitle: activity.title, applied: { state.answer(.applied) },
                                       notYet: { state.answer(.notYet) }, neverAsk: { state.answer(.neverAsk) })
                    .presentationDetents([.medium])
            }
        }
    }
    private func card(_ item: RecommendedActivity, index: Int, insets: EdgeInsets) -> some View {
        let current = (position ?? model.items.first?.id) == item.id
        return QueueStoryCard(
            item: item, position: index + 1, count: model.items.count, current: current,
            playing: detail == nil && choosing == nil && scenePhase == .active, liked: model.liked.contains(item.id), insets: insets, now: now,
            toggleLike: { model.toggleLike(item) }, doubleTapLike: { model.like(item) },
            detail: {
                state.remember(item.activity)
                model.viewed(item, .openDetail)
                detail = item
            },
            next: { move(after: index) }, more: { choosing = item },
            complete: { model.viewed(item, .complete) }, skipEarly: { model.viewed(item, .skipEarly) })
    }
    /// The card (and with `이 프로그램 숨기기` its program's others) leaves; the next remaining card takes its page.
    private func choose(_ choice: DiscoveryQueueModel.Choice, _ item: RecommendedActivity) {
        let following = model.items.drop { $0.id != item.id }.dropFirst().map(\.id)
        model.choose(choice, item)
        position = following.first { id in model.items.contains { $0.id == id } } ?? Self.endID
    }
    private func move(after index: Int) {
        let next = model.items.indices.contains(index + 1) ? model.items[index + 1].id : Self.endID
        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.3)) { position = next }
    }
    @ViewBuilder private var endPage: some View {
        if model.loading {
            ProgressView().tint(.white).frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if model.failed && model.items.isEmpty {
            CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "대기열을 불러오지 못했어요", message: "연결을 확인한 뒤 다시 시도해 주세요.",
                            primary: MessageAction("다시 시도", identifier: "queue-retry") { Task { await model.load(preferences) } })
        } else {
            CenteredMessage(symbol: "checkmark.circle", title: "오늘 대기열을 다 봤어요", message: "남은 후보로 대기열을 다시 만들 수 있어요.",
                            primary: MessageAction("더 받기", identifier: "queue-refill") {
                                Task {
                                    await model.refill(preferences)
                                    now = Date()
                                    position = model.items.first?.id ?? Self.endID
                                }
                            })
        }
    }
    /// The `되돌리기` notice of the last ⋯ choice; recorded when it ends.
    @ViewBuilder private var notice: some View {
        if let pending = model.pending {
            HStack(spacing: 12) {
                Text(pending.choice.notice).font(.dearby(.subheadline)).foregroundStyle(.primary)
                Spacer(minLength: 8)
                Button("되돌리기") {
                    model.undo()
                    position = pending.item.id
                }.font(.dearby(.subheadline).weight(.semibold))
            }
            .padding(.horizontal, 16).frame(minHeight: 48)
            .background(.regularMaterial, in: .capsule)
            .padding(.horizontal, 16).padding(.bottom, 12)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .task(id: pending.item.id) {
                AccessibilityNotification.Announcement(pending.choice.notice).post()
                try? await Task.sleep(for: .seconds(4))
                if !Task.isCancelled { withAnimation { model.commitPending() } }
            }
        }
    }
}

/// Presents `DiscoveryQueuePage` from the 발견 tab's 추천 section.
struct DiscoveryQueueEntry: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: "rectangle.stack.fill").font(.dearby(.body)).foregroundStyle(DearbyStyle.teal)
                Text("오늘의 맞춤 대기열").font(.dearby(.body).weight(.semibold)).foregroundStyle(.primary)
                Spacer(minLength: 12)
                Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
            }
            .frame(minHeight: 44).contentShape(Rectangle())
        }
        .buttonStyle(.plain).accessibilityIdentifier("reco-queue")
    }
}
