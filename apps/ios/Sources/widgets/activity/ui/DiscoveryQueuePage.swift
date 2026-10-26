import SwiftUI

/// 오늘의 맞춤 대기열 (contract "맞춤 대기열(스토리형 쇼츠)" 2026-10-24): full-screen vertical pages (`.paging`), one activity each
/// (`QueueStoryCard`), then `오늘 대기열을 다 봤어요` with `더 받기`. `자세히` opens the activity detail in a sheet so the place in the queue
/// stays; `⋯` is the system confirmation dialog, and a choice shows a `되돌리기` notice for 4 seconds before it is recorded. After the last card and
/// before the end comes one AdMob native ad page (contract "맞춤 대기열 끝 광고(AdMob, 비개인화)" 2026-10-25) when it has loaded by then.
struct DiscoveryQueuePage: View {
    let model: DiscoveryQueueModel
    let state: ActivityViewModel
    /// Asked right before the ad request; no ad (no SDK, no ATT prompt) unless agreed.
    let adConsent: AdConsentModel
    @Environment(FavoritesModel.self) private var favorites
    @State private var position: String?
    @State private var detail: RecommendedActivity?
    @State private var choosing: RecommendedActivity?
    @State private var now = Date()
    /// 사용 통계: cards on screen for a second this time (`queue_card_view` once each), the last card shown, and whether the end was reached.
    @State private var viewedIDs: Set<String> = []
    @State private var lastPosition = 0
    @State private var reachedEnd = false
    /// The ad of this opening: its page follows the last card once loaded, until the end page is reached without it or `더 받기` (one per opening).
    /// Closing on it logs `queue_exit` with the last activity card's number (`lastPosition`; the ad page does not count).
    @State private var ads = QueueAdLoader()
    @State private var adSlot = AdSlot.open
    private enum AdSlot { case open, shown, closed }
    private var showsAd: Bool { adSlot != .closed && ads.nativeAd != nil && !model.items.isEmpty }
    private var afterCards: String { showsAd ? Self.adID : Self.endID }
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private static let endID = "queue-end"
    private static let adID = "queue-ad"
    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(Array(model.items.enumerated()), id: \.element.id) { index, item in
                        card(item, index: index, insets: proxy.safeAreaInsets).containerRelativeFrame([.horizontal, .vertical])
                    }
                    if showsAd, let nativeAd = ads.nativeAd {
                        QueueAdView(nativeAd: nativeAd).padding(proxy.safeAreaInsets).containerRelativeFrame([.horizontal, .vertical]).id(Self.adID)
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
        .task {
            await model.load()
            AppAnalytics.event("queue_open", ["size": model.items.count, "seen": model.items.filter { favorites.isFavorite(.activity($0.id)) }.count])
            if !model.items.isEmpty, await adConsent.allowsQueueAd() { await ads.load() }
        }
        .analyticsScreen("queue")
        .adConsentSheet(adConsent, at: .queue)
        .onChange(of: position) { _, position in
            if position == Self.adID, adSlot == .open {
                adSlot = .shown
                AppAnalytics.event("queue_ad_shown")
            } else if position == Self.endID, adSlot == .open, !model.items.isEmpty {
                adSlot = .closed
                if ads.requested, ads.nativeAd == nil { AppAnalytics.event("queue_ad_skipped_unloaded", ["reason": ads.failed ? "failed" : "loading"]) }
            }
            guard position == Self.endID, !model.items.isEmpty, !reachedEnd else { return }
            reachedEnd = true
            AppAnalytics.event("queue_end", ["viewed": viewedIDs.count])
        }
        .onDisappear {
            model.commitPending()
            if !reachedEnd { AppAnalytics.event("queue_exit", ["position": lastPosition]) }
        }
        // `+` on an added activity opens it in 내 활동: the queue gives way to that tab.
        .onChange(of: state.showInMine) { _, id in if id != nil { dismiss() } }
        .confirmationDialog("이 활동", isPresented: Binding(get: { choosing != nil }, set: { if !$0 { choosing = nil } }),
                            titleVisibility: .hidden, presenting: choosing) { item in
            ForEach(DiscoveryQueueModel.Choice.allCases.filter { $0 != .lessField || !item.activity.fieldTags.isEmpty }) { choice in
                Button(choice.label) { choose(choice, item) }
            }
        }
        .sheet(item: $detail) { item in
            NavigationStack {
                ActivityDetailView(state: state, activityID: item.id, source: "queue")
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
        let analytics = AppAnalytics.activity(item.id, item.activity).merging(["position": index + 1]) { $1 }
        let logLike = { if !favorites.isFavorite(.activity(item.id)) { AppAnalytics.event("queue_like", analytics) } }
        return QueueStoryCard(
            item: item, position: index + 1, count: model.items.count, current: current,
            playing: detail == nil && choosing == nil && scenePhase == .active, level: favorites.levels[.activity(item.id)],
            added: state.appliedIDs.contains(item.id), insets: insets, now: now,
            toggleLike: {
                logLike()
                favorites.toggle(.activity(item.id))
            },
            doubleTapLike: {
                logLike()
                favorites.add(.activity(item.id))
            },
            setLevel: { favorites.setLevel(.activity(item.id), $0) },
            addToMine: {
                state.remember(item.activity)
                state.addToMine(item.id, source: "queue")
            },
            detail: {
                AppAnalytics.event("queue_detail", ["position": index + 1, "activity_id": item.id])
                state.remember(item.activity)
                model.viewed(item, .openDetail)
                detail = item
            },
            next: { move(after: index) }, more: { choosing = item },
            complete: { scenes in
                AppAnalytics.event("queue_card_complete", ["position": index + 1, "scenes": scenes])
                model.viewed(item, .complete)
            },
            skipEarly: { model.viewed(item, .skipEarly) })
        // `queue_card_view`: a second on screen.
        .task(id: current) {
            guard current else { return }
            lastPosition = index + 1
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled, viewedIDs.insert(item.id).inserted else { return }
            AppAnalytics.event("queue_card_view", analytics)
        }
    }
    /// The card (and with `이 프로그램 숨기기` its program's others) leaves; the next remaining card takes its page.
    private func choose(_ choice: DiscoveryQueueModel.Choice, _ item: RecommendedActivity) {
        let following = model.items.drop { $0.id != item.id }.dropFirst().map(\.id)
        let index = model.items.firstIndex { $0.id == item.id } ?? 0
        AppAnalytics.event("queue_dismiss", AppAnalytics.activity(item.id, item.activity).merging(["position": index + 1, "reason": choice.signal.rawValue.lowercased()]) { $1 })
        model.choose(choice, item)
        position = following.first { id in model.items.contains { $0.id == id } } ?? afterCards
    }
    private func move(after index: Int) {
        let next = model.items.indices.contains(index + 1) ? model.items[index + 1].id : afterCards
        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.3)) { position = next }
    }
    @ViewBuilder private var endPage: some View {
        if model.loading {
            ProgressView().tint(.white).frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if model.failed && model.items.isEmpty {
            CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "대기열을 불러오지 못했어요", message: "연결을 확인한 뒤 다시 시도해 주세요.",
                            primary: MessageAction("다시 시도", identifier: "queue-retry") { Task { await model.load() } })
        } else if model.items.isEmpty {
            // Nothing today, also when the server has no queue yet (404): the entry stays, this says so (as Android and web).
            CenteredMessage(symbol: "sparkles", title: "아직 대기열이 없어요", message: "추천 설정을 마치면 맞춤 대기열을 볼 수 있어요.",
                            primary: MessageAction("닫기", identifier: "queue-close") { dismiss() })
        } else {
            CenteredMessage(symbol: "checkmark.circle", title: "오늘 대기열을 다 봤어요", message: "남은 후보로 대기열을 다시 만들 수 있어요.",
                            primary: MessageAction("더 받기", identifier: "queue-refill") {
                                Task {
                                    adSlot = .closed
                                    await model.refill()
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

/// Presents `DiscoveryQueuePage` from the top of the 발견 tab; `설정` reopens the 추천 funnel. Signed out (contract "비로그인에서 만들기·추천을
/// 막음" 2026-10-25) the row says signing in comes first and has no `설정`; the app opens the sign-in sheet. Signed in without the consent
/// ([needsConsent], contract "개인정보 동의") it says the consent comes first; the app opens the consent sheet.
struct DiscoveryQueueEntry: View {
    let signedIn: Bool
    var needsConsent = false
    let action: () -> Void
    let settings: () -> Void
    var body: some View {
        HStack(spacing: 4) {
            Button(action: action) {
                HStack(spacing: 12) {
                    Image(systemName: "rectangle.stack.fill").font(.dearby(.body)).foregroundStyle(DearbyStyle.teal)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("오늘의 맞춤 대기열").font(.dearby(.body).weight(.semibold)).foregroundStyle(.primary)
                        if !signedIn { Text(needsConsent ? "동의하면 나에게 맞는 활동을 골라 드려요" : "로그인하면 나에게 맞는 활동을 골라 드려요").font(.dearby(.footnote)).foregroundStyle(.secondary) }
                    }
                    Spacer(minLength: 12)
                    Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
                }
                .frame(minHeight: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain).accessibilityIdentifier("reco-queue")
            if signedIn {
                Button("설정", action: settings).font(.dearby(.subheadline)).frame(minWidth: 44, minHeight: 44)
                    .accessibilityLabel("추천 설정").accessibilityIdentifier("reco-settings")
            }
        }
    }
}
