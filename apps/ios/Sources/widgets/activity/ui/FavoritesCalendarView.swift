import SwiftUI
import UIKit

/// 즐겨찾기 활동 캘린더 보기 (contract 2026-10-25): the system month calendar (`UICalendarView`) with a dot under each day — 마감 a filled teal dot,
/// 모집 시작 a hollow mint ring, 행사 a filled grey dot — and under it the dates by month like the 일정표 mock: the day number and weekday on the
/// left, each activity with its program and `마감`/`모집 시작`/`행사` on the right (a deadline within 3 days in red), past days dimmed, then
/// [undated] with the activities that have no date. Choosing a day scrolls the list to it; paging the month scrolls to that month. In-app only:
/// nothing is written to the device calendar.
struct FavoritesCalendarView<Undated: View>: View {
    let items: [FavoriteActivity]
    /// Days with a favorited session (세션 찜, contract "트랙·세션 시간표"): a pink dot only, the list stays activities.
    var sessionDays: Set<SeoulDay> = []
    let open: (String) -> Void
    @ViewBuilder let undated: ([FavoriteActivity]) -> Undated
    @State private var target: String?
    var body: some View {
        let (dated, dateless) = FavoriteDate.build(items)
        let days = Dictionary(grouping: dated, by: \.day)
        let today = SeoulDay(Date())
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    FavoriteMonthCalendar(marks: days.mapValues { Set($0.map(\.kind)) }, sessionDays: sessionDays) { day in
                        // The chosen day, else the next day with something on it.
                        target = days.keys.filter { $0 >= day }.min().map(Self.dayID)
                    } monthChanged: { month in
                        if days.keys.contains(where: { $0.month1 == month }) { target = Self.monthID(month) }
                    }
                    .padding(.horizontal, 8)
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(months(days.keys), id: \.self) { month in
                            monthHeader(month).id(Self.monthID(month))
                            ForEach(days.keys.filter { $0.month1 == month }.sorted(), id: \.self) { day in
                                dayRow(day, entries: days[day] ?? [], today: today).id(Self.dayID(day))
                            }
                        }
                    }
                    undated(dateless).padding(.bottom, 24)
                }
            }
            .onChange(of: target) { _, id in
                guard let id else { return }
                withAnimation { proxy.scrollTo(id, anchor: .top) }
                target = nil
            }
        }
    }
    private static func dayID(_ day: SeoulDay) -> String { "day-\(day.year)-\(day.month)-\(day.day)" }
    private static func monthID(_ month: SeoulDay) -> String { "month-\(month.year)-\(month.month)" }
    private func months(_ days: Dictionary<SeoulDay, [FavoriteDate]>.Keys) -> [SeoulDay] { Set(days.map(\.month1)).sorted() }
    private func monthHeader(_ month: SeoulDay) -> some View {
        header(ActivityText.format(month.date, "yyyy년 M월", zone: ActivityText.seoul))
    }
    private func header(_ text: String) -> some View {
        Text(text).font(.dearby(.headline)).foregroundStyle(.primary).accessibilityAddTraits(.isHeader)
            .padding(.horizontal, 20).padding(.top, 20).padding(.bottom, 6)
    }
    private func dayRow(_ day: SeoulDay, entries: [FavoriteDate], today: SeoulDay) -> some View {
        let left = SeoulDay.calendar.dateComponents([.day], from: today.date, to: day.date).day ?? 0
        return HStack(alignment: .top, spacing: 14) {
            VStack(spacing: 0) {
                Text("\(day.day)").font(.dearby(.title2).weight(.bold)).monospacedDigit()
                Text(ActivityText.format(day.date, "E", zone: ActivityText.seoul)).font(.dearby(.caption)).foregroundStyle(.secondary)
            }
            .frame(width: 40).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 10) {
                ForEach(entries) { date in
                    entry(title: date.item.activity.title, program: date.item.programName, label: date.kind.label,
                          urgent: date.kind == .deadline && (0...3).contains(left)) { open(date.item.id) }
                        .accessibilityLabel([ActivityText.format(day.date, "M월 d일 EEEE", zone: ActivityText.seoul), date.item.activity.title,
                                             date.item.programName, date.kind.label].compactMap { $0 }.joined(separator: ", "))
                }
            }
        }
        .padding(.horizontal, 20).padding(.vertical, 8)
        .opacity(day < today ? 0.45 : 1)
    }
    private func entry(title: String, program: String?, label: String?, urgent: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(.primary).multilineTextAlignment(.leading)
                    if let program { Text(program).font(.dearby(.footnote)).foregroundStyle(.secondary) }
                }
                Spacer(minLength: 8)
                if let label {
                    Text(label).font(.dearby(.caption).weight(.semibold))
                        .foregroundStyle(urgent ? DearbyStyle.danger : DearbyStyle.teal)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(urgent ? DearbyStyle.danger.opacity(0.12) : DearbyStyle.mint, in: Capsule())
                }
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 14, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain).accessibilityHint("누르면 활동 상세를 보여요")
    }
}

/// `UICalendarView` in Seoul days with one decoration per marked day and single-day selection; SwiftUI has no month calendar of its own.
private struct FavoriteMonthCalendar: UIViewRepresentable {
    let marks: [SeoulDay: Set<FavoriteDate.Kind>]
    let sessionDays: Set<SeoulDay>
    let select: (SeoulDay) -> Void
    let monthChanged: (SeoulDay) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    func makeUIView(context: Context) -> UICalendarView {
        let view = UICalendarView()
        view.calendar = SeoulDay.calendar
        view.locale = Locale(identifier: "ko_KR")
        view.timeZone = ActivityText.seoul
        view.tintColor = DearbyStyle.tealUIColor
        view.delegate = context.coordinator
        view.selectionBehavior = UICalendarSelectionSingleDate(delegate: context.coordinator)
        context.coordinator.marks = marks
        context.coordinator.sessionDays = sessionDays
        return view
    }
    func updateUIView(_ view: UICalendarView, context: Context) {
        let old = context.coordinator.marks, oldSessions = context.coordinator.sessionDays
        context.coordinator.parent = self
        context.coordinator.marks = marks
        context.coordinator.sessionDays = sessionDays
        guard old != marks || oldSessions != sessionDays else { return }
        view.reloadDecorations(forDateComponents: Set(old.keys).union(marks.keys).union(oldSessions).union(sessionDays).map(\.components), animated: false)
    }
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UICalendarView, context: Context) -> CGSize? {
        let width = proposal.width ?? 360
        let fitted = uiView.systemLayoutSizeFitting(CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                                                    withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
        return CGSize(width: width, height: fitted.height)
    }
    final class Coordinator: NSObject, UICalendarViewDelegate, UICalendarSelectionSingleDateDelegate {
        var parent: FavoriteMonthCalendar
        var marks: [SeoulDay: Set<FavoriteDate.Kind>] = [:]
        var sessionDays: Set<SeoulDay> = []
        init(_ parent: FavoriteMonthCalendar) { self.parent = parent }
        func calendarView(_ calendarView: UICalendarView, decorationFor dateComponents: DateComponents) -> UICalendarView.Decoration? {
            guard let day = SeoulDay(dateComponents) else { return nil }
            let kinds = marks[day] ?? [], session = sessionDays.contains(day)
            guard !kinds.isEmpty || session else { return nil }
            return .customView { Self.dots(kinds, session: session) }
        }
        func dateSelection(_ selection: UICalendarSelectionSingleDate, didSelectDate dateComponents: DateComponents?) {
            if let day = dateComponents.flatMap(SeoulDay.init) { parent.select(day) }
        }
        func calendarView(_ calendarView: UICalendarView, didChangeVisibleDateComponentsFrom previousDateComponents: DateComponents) {
            if let year = calendarView.visibleDateComponents.year, let month = calendarView.visibleDateComponents.month {
                parent.monthChanged(SeoulDay(year: year, month: month, day: 1))
            }
        }
        /// 마감 filled teal, 모집 시작 hollow mint, 행사 filled grey, then a pink 찜한 세션 dot, side by side.
        private static func dots(_ kinds: Set<FavoriteDate.Kind>, session: Bool) -> UIView {
            let stack = UIStackView()
            stack.axis = .horizontal
            stack.spacing = 2
            for kind in [FavoriteDate.Kind.deadline, .start, .event] where kinds.contains(kind) {
                let dot = UIView()
                dot.translatesAutoresizingMaskIntoConstraints = false
                dot.layer.cornerRadius = 3.5
                switch kind {
                case .deadline: dot.backgroundColor = DearbyStyle.tealUIColor
                case .start: dot.layer.borderWidth = 1.5; dot.layer.borderColor = UIColor.systemMint.cgColor
                case .event: dot.backgroundColor = .systemGray
                }
                NSLayoutConstraint.activate([dot.widthAnchor.constraint(equalToConstant: 7), dot.heightAnchor.constraint(equalToConstant: 7)])
                stack.addArrangedSubview(dot)
            }
            if session {
                let dot = UIView()
                dot.translatesAutoresizingMaskIntoConstraints = false
                dot.layer.cornerRadius = 3.5
                dot.backgroundColor = .systemPink
                NSLayoutConstraint.activate([dot.widthAnchor.constraint(equalToConstant: 7), dot.heightAnchor.constraint(equalToConstant: 7)])
                stack.addArrangedSubview(dot)
            }
            return stack
        }
    }
}
