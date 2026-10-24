import SwiftUI

/// 맞춤 대기열의 활동 한 장(contract "맞춤 대기열(스토리형 쇼츠)" 2026-10-24, 시안 Q3): 장면(서버 `scenes`, 없으면 앱이 만든 2~3개)을 4초마다 넘기고(위 진행 막대, 왼쪽 30% 탭은 이전·나머지는
/// 다음 장면, 두 번 탭은 관심 있어요와 하트 효과), 배경 커버는 느리게 확대·이동(켄 번즈)하고 글자는 아래에서 차례로 떠오른다. 동작 줄이기에서는 움직임 없이
/// 장면만 바뀌고, 화면 읽기를 켜면 저절로 넘기지 않고 사용자 지정 동작으로 같은 일을 한다. 마지막 장면이 끝나면 `complete`(그 장면에 멈춘다), 장면 ①에서 떠나면 `skipEarly`.
struct QueueStoryCard: View {
    let item: RecommendedActivity
    let position: Int
    let count: Int
    /// The page on screen; `playing` also needs nothing over it (⋯ dialog, detail) and the app in the foreground.
    let current: Bool
    let playing: Bool
    let liked: Bool
    let insets: EdgeInsets
    let now: Date
    let toggleLike: () -> Void
    let doubleTapLike: () -> Void
    let detail: () -> Void
    let next: () -> Void
    let more: () -> Void
    let complete: () -> Void
    let skipEarly: () -> Void
    @State private var scene = 0
    /// The last scene ran its 4 seconds: the card stays there with every segment full until the user moves on (no auto-advance to the next card).
    @State private var finished = false
    @State private var zoomed = false
    @State private var bursts = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    private var activity: ActivityModel { item.activity }
    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .bottomLeading) {
                cover(proxy.size).accessibilityHidden(true)
                LinearGradient(stops: [.init(color: .black.opacity(0.45), location: 0), .init(color: .black.opacity(0.05), location: 0.3),
                                       .init(color: .black.opacity(0.1), location: 0.55), .init(color: .black.opacity(0.85), location: 1)],
                               startPoint: .top, endPoint: .bottom)
                    .accessibilityHidden(true)
                tapLayer(width: proxy.size.width)
                if bursts > 0 {
                    Image(systemName: "heart.fill").font(.system(size: 110)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .transition(.scale(scale: 0.3).combined(with: .opacity)).allowsHitTesting(false).accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: 10) {
                    sceneContent
                }
                .padding(.leading, 18).padding(.trailing, 84).padding(.bottom, insets.bottom + 40)
                .allowsHitTesting(false)
                .accessibilityElement(children: .combine)
                .accessibilityHint("장면 \(scene + 1)/\(scenes.count)")
                .accessibilityActions { actions }
                rail.frame(maxWidth: .infinity, alignment: .trailing).padding(.trailing, 10).padding(.bottom, insets.bottom + 46)
            }
            .overlay(alignment: .top) { header.padding(.top, insets.top + 8) }
        }
        .foregroundStyle(.white)
        .background(.black)
        .clipped()
        .task(id: "\(current)|\(playing)|\(voiceOver)|\(scene)") { await advance() }
        .onChange(of: current && playing && !reduceMotion, initial: true) { _, moving in
            if moving {
                withAnimation(.easeInOut(duration: 9).repeatForever(autoreverses: true)) { zoomed = true }
            } else {
                // An animated change replaces the repeating one.
                withAnimation(.easeOut(duration: 0.3)) { zoomed = false }
            }
        }
        .onChange(of: current) { _, onScreen in if !onScreen, scene == 0 { skipEarly() } }
    }
    /// Every 4 seconds the next scene; after the last, `complete` once and the card stays on it.
    private func advance() async {
        guard current, playing, !voiceOver, !finished else { return }
        try? await Task.sleep(for: .seconds(4))
        guard !Task.isCancelled else { return }
        if scene < last {
            scene += 1
        } else {
            finished = true
            complete()
        }
    }
    private func step(_ delta: Int) {
        scene = min(max(scene + delta, 0), last)
        // Going back to an earlier scene plays from there again (COMPLETE is still sent once per card).
        if scene < last { finished = false }
    }
    private func burstLike() {
        doubleTapLike()
        guard !reduceMotion else { return }
        withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) { bursts += 1 }
        Task {
            try? await Task.sleep(for: .milliseconds(800))
            withAnimation(.easeOut(duration: 0.2)) { bursts = 0 }
        }
    }
    /// Left 30%: previous scene; the rest: next. Double tap anywhere: 관심 있어요. Hidden from VoiceOver (custom actions do the same).
    private func tapLayer(width: CGFloat) -> some View {
        Color.clear.contentShape(Rectangle())
            .gesture(SpatialTapGesture(count: 2).onEnded { _ in burstLike() }
                .exclusively(before: SpatialTapGesture().onEnded { tap in step(tap.location.x < width * 0.3 ? -1 : 1) }))
            .accessibilityHidden(true)
    }
    @ViewBuilder private var actions: some View {
        Button("다음 장면") { step(1) }
        Button("이전 장면") { step(-1) }
        Button(liked ? "관심 취소" : "관심 있어요", action: toggleLike)
        Button("자세히", action: detail)
        Button("다음 활동", action: next)
        Button("더 보기", action: more)
    }
    // MARK: - Cover

    /// The program's 세로 커버, slowly zooming and drifting; without one, the logo on a teal gradient (no brand color is in the API).
    private func cover(_ size: CGSize) -> some View {
        AsyncImage(url: item.coverURL) { phase in
            if let image = phase.image { image.resizable().scaledToFill() } else { fallbackCover }
        }
        .frame(width: size.width, height: size.height)
        .scaleEffect(zoomed ? 1.22 : 1.05)
        .offset(x: zoomed ? -size.width * 0.04 : 0, y: zoomed ? -size.height * 0.03 : 0)
        .clipped()
    }
    private var fallbackCover: some View {
        LinearGradient(colors: [DearbyStyle.tealFill, DearbyStyle.tealFill.opacity(0.55), .black], startPoint: .topLeading, endPoint: .bottomTrailing)
            .overlay(alignment: .center) { logo(size: 96).offset(y: -120) }
    }
    private func logo(size: CGFloat) -> some View {
        AsyncImage(url: item.logoURL) { phase in
            if let image = phase.image {
                image.resizable().scaledToFit()
            } else {
                Text(String((item.programName ?? activity.organization).prefix(1))).font(.system(size: size * 0.45, weight: .bold))
                    .foregroundStyle(Color(.darkGray))
            }
        }
        .frame(width: size, height: size).background(.white).clipShape(.rect(cornerRadius: size * 0.26, style: .continuous))
    }
    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 4) {
                ForEach(scenes.indices, id: \.self) { index in
                    ProgressSegment(state: index < scene || finished ? .done : index > scene ? .waiting : .playing(current && playing && !voiceOver && !reduceMotion))
                        .id("\(index)-\(scene)-\(current && playing)")
                }
            }
            HStack(spacing: 8) {
                logo(size: 30)
                Text(activity.organization).fontWeight(.semibold).lineLimit(1)
                Text("· 오늘의 맞춤 대기열 \(position)/\(count)").opacity(0.75).lineLimit(1)
            }
            .font(.dearby(.subheadline)).padding(.trailing, 44)
        }
        .padding(.horizontal, 12)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(activity.organization), 오늘의 맞춤 대기열 \(position)/\(count)")
    }
    // MARK: - Scenes

    /// The server's `scenes` when it sends any; else hook, 핵심 줄 (two or more), deadline.
    private var scenes: [QueueScene] {
        if let scenes = item.scenes, !scenes.isEmpty { return scenes }
        let hook = QueueScene.hook(hook: activity.hook ?? activity.title,
                                   name: activity.hook == nil ? item.programName ?? activity.organization : activity.title)
        let deadline = QueueScene.deadline(label: nil, days: days, deadlineText: deadlineLine, reasons: item.reasons)
        return lines.count >= 2 ? [hook, .highlights(lines), deadline] : [hook, deadline]
    }
    private var last: Int { scenes.count - 1 }
    @ViewBuilder private var sceneContent: some View {
        switch scenes[min(scene, last)] {
        case let .hook(hook, name):
            let tag = [activity.category?.label, activity.fieldTags.first?.name].compactMap { $0 }.joined(separator: " · ")
            if !tag.isEmpty { Rise(order: 0) { label(tag) } }
            Rise(order: 1) { Text(hook).font(.dearby(.largeTitle).weight(.heavy)).lineLimit(4).minimumScaleFactor(0.7) }
            if !name.isEmpty { Rise(order: 2) { Text(name).font(.dearby(.subheadline)).opacity(0.9) } }
        case .highlights(let lines):
            Rise(order: 0) { label("이런 걸 해요") }
            ForEach(Array(lines.prefix(3).enumerated()), id: \.offset) { index, line in
                Rise(order: index + 1) {
                    HStack(spacing: 10) {
                        Image(systemName: Self.symbol(line.icon)).font(.dearby(.body)).frame(width: 36, height: 36)
                            .background(.white.opacity(0.18), in: Circle())
                        Text(line.text).font(.dearby(.title3).weight(.bold)).lineLimit(2)
                    }
                }
            }
        case .eligibility(let text):
            Rise(order: 0) { label("지원 대상") }
            Rise(order: 1) { Text(text).font(.dearby(.title2).weight(.bold)).lineLimit(5) }
        case .benefit(let text):
            Rise(order: 0) { label("혜택") }
            Rise(order: 1) { Text(text).font(.dearby(.title2).weight(.bold)).lineLimit(5) }
        case let .deadline(heading, days, deadlineText, reasons):
            let countdown = countdown(label: heading, days: days)
            Rise(order: 0) { label(countdown.heading) }
            if let dday = countdown.dday { Rise(order: 1) { Text(dday).font(.system(size: 64, weight: .heavy)) } }
            if let deadlineText { Rise(order: 2) { Text(deadlineText).font(.dearby(.callout)).opacity(0.9) } }
            if !reasons.isEmpty {
                Rise(order: 3) {
                    ChipFlow(spacing: 6) {
                        ForEach(reasons, id: \.self) { reason in
                            Text(reason).font(.dearby(.footnote).weight(.semibold)).padding(.horizontal, 10).padding(.vertical, 4)
                                .background(.white.opacity(0.22), in: Capsule())
                        }
                    }
                }
            }
        }
    }
    private func label(_ text: String) -> some View {
        Text(text).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(Self.accent)
    }
    /// `highlights`, or (contract: 없으면 기간·장소; cost is not in the public activity) the period and place; the summary when neither is known.
    private var lines: [ActivityHighlight] {
        if !activity.highlights.isEmpty { return Array(activity.highlights.prefix(3)) }
        let fallback = [activity.scheduleText(at: now).map { ActivityHighlight(icon: .period, text: $0) },
                        (activity.place?.name ?? activity.location).map { ActivityHighlight(icon: .place, text: $0) }].compactMap { $0 }
        return fallback.isEmpty ? [ActivityHighlight(icon: nil, text: activity.summary)] : fallback
    }
    /// A conference counts down to the event (`행사까지`), everything else to the application deadline (`마감까지`).
    private var isEvent: Bool { activity.category == .conference }
    /// The counted day: a conference's start; else the last day to apply (a timed window's end, or the day before a date-only window's
    /// exclusive end).
    private var lastDay: (date: Date, zone: TimeZone)? {
        if isEvent, let stage = activity.activityStage, let start = stage.start {
            return (start, stage.allDay ? ActivityText.seoul : TimeZone(identifier: stage.timeZone) ?? ActivityText.seoul)
        }
        guard let stage = activity.applicationStage, let end = stage.end else { return nil }
        if stage.allDay { return Self.seoul.date(byAdding: .day, value: -1, to: end).map { ($0, ActivityText.seoul) } }
        return (end, TimeZone(identifier: stage.timeZone) ?? ActivityText.seoul)
    }
    /// Seoul days to the counted day; nil without one.
    private var days: Int? {
        lastDay.flatMap { Self.seoul.dateComponents([.day], from: Self.seoul.startOfDay(for: now), to: Self.seoul.startOfDay(for: $0.date)).day }
    }
    /// The countdown as web shows it (PR #336): the server's `label` (else `행사까지` for a 컨퍼런스, `마감까지` otherwise) with `D-n`/`D-DAY`;
    /// once past, `모집 마감`/`행사 종료` alone; without a count, the heading alone.
    private func countdown(label: String?, days: Int?) -> (heading: String, dday: String?) {
        let label = label ?? (isEvent ? "행사까지" : "마감까지")
        guard let days else { return (label, nil) }
        if days < 0 { return (label == "행사까지" ? "행사 종료" : "모집 마감", nil) }
        return (label, days == 0 ? "D-DAY" : "D-\(days)")
    }
    /// `11월 15일 (일) 마감 · 나이 19~34세`.
    private var deadlineLine: String? {
        let deadline = lastDay.map { ActivityText.format($0.date, "M월 d일 (E)", zone: $0.zone) + (isEvent ? " 행사" : " 마감") }
            ?? activity.recruitmentText(at: now)
        let audience = activity.eligibility.map { EligibilityText.chips($0).prefix(2).joined(separator: " · ") }
        let parts = [deadline, audience].compactMap { $0 }.filter { !$0.isEmpty }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
    // MARK: - Rail

    private var rail: some View {
        VStack(spacing: 16) {
            RailButton(symbol: liked ? "heart.fill" : "heart", title: "관심", label: liked ? "관심 취소" : "관심 있어요", tint: liked ? .pink : .white,
                       bounce: liked && !reduceMotion, action: toggleLike)
                .accessibilityAddTraits(liked ? .isSelected : [])
            RailButton(symbol: "info.circle", title: "자세히", label: "자세히 보기", action: detail)
            RailButton(symbol: "chevron.up", title: "다음", label: "다음 활동", action: next)
            RailButton(symbol: "ellipsis", title: nil, label: "더 보기", action: more)
        }
    }
    private static let accent = Color(red: 0.5, green: 0.89, blue: 0.89)
    private static let seoul: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = ActivityText.seoul
        return calendar
    }()
    static func symbol(_ icon: ActivityHighlight.Icon?) -> String {
        switch icon {
        case .cost: "wonsign.circle"
        case .period: "calendar"
        case .team: "person.3"
        case .mentor: "person.2"
        case .award: "trophy"
        case .place: "mappin.and.ellipse"
        case .talk: "mic"
        case .ticket: "ticket"
        case nil: "text.alignleft"
        }
    }
}

/// One segment of the progress bar: full for past scenes, filling over 4 seconds for the playing one (at once with reduced motion).
private struct ProgressSegment: View {
    enum State: Equatable { case done, waiting, playing(Bool) }
    let state: State
    @SwiftUI.State private var filled = false
    var body: some View {
        Capsule().fill(.white.opacity(0.35)).frame(height: 3)
            .overlay(alignment: .leading) {
                Capsule().fill(.white).scaleEffect(x: fraction, anchor: .leading)
            }
            .onAppear {
                if case .playing(true) = state { withAnimation(.linear(duration: 4)) { filled = true } }
            }
            .accessibilityHidden(true)
    }
    private var fraction: CGFloat {
        switch state {
        case .done: 1
        case .waiting: 0
        case .playing(let animated): animated ? (filled ? 1 : 0) : 1
        }
    }
}

/// A scene line rising from below in turn (0.15 s apart); shown at once with reduced motion.
private struct Rise<Content: View>: View {
    let order: Int
    @ViewBuilder let content: Content
    @State private var shown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        content
            .offset(y: shown || reduceMotion ? 0 : 18).opacity(shown || reduceMotion ? 1 : 0)
            .onAppear { withAnimation(.easeOut(duration: 0.5).delay(0.15 * Double(order))) { shown = true } }
    }
}

/// A round control of the right rail with its caption.
private struct RailButton: View {
    let symbol: String
    let title: String?
    let label: String
    var tint: Color = .white
    var bounce = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: symbol).font(.title2).foregroundStyle(tint)
                    .symbolEffect(.bounce, value: bounce)
                    .frame(width: 48, height: 48).background(.black.opacity(0.35), in: Circle())
                if let title { Text(title).font(.dearby(.caption)) }
            }
            .frame(minWidth: 56)
        }
        .buttonStyle(.plain).foregroundStyle(.white)
        .accessibilityLabel(label)
    }
}
