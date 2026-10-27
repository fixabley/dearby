import SwiftUI

/// 오늘 행사 온보딩 시트(contract "오늘 행사 온보딩" 2026-10-27): 제목 `오늘 참여하는 행사가 있나요?` 아래 오늘 열리는 행사 행(로고·이름·장소·오늘 시간 범위,
/// 최대 5개, 여러 개 선택)을 inset-grouped `List`로 보이고, 아래에 주 버튼 `참여해요`(고른 것이 없으면 비활성)와 텍스트 버튼 `오늘은 아니에요`를 둔다
/// (신청 확인 시트와 같은 버튼 배치). 행이 하나면 처음부터 골라 둔다.
struct TodayOnboardingSheet: View {
    let candidates: [TodayCandidate]
    let logoURL: (Int64) -> URL
    let join: ([TodayCandidate]) -> Void
    let skip: () -> Void
    @State private var chosen: Set<String>
    init(candidates: [TodayCandidate], logoURL: @escaping (Int64) -> URL, join: @escaping ([TodayCandidate]) -> Void, skip: @escaping () -> Void) {
        self.candidates = candidates
        self.logoURL = logoURL
        self.join = join
        self.skip = skip
        _chosen = State(initialValue: candidates.count == 1 ? [candidates[0].id] : [])
    }
    var body: some View {
        List {
            Section { ForEach(candidates) { row($0) } }
        }
        // The heading sits above the list: a List section header takes the header style and greys the title out.
        .safeAreaInset(edge: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: 6) {
                Text("오늘 참여하는 행사가 있나요?").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
                Text("고른 행사를 내 활동에 넣고 오늘 시간표를 열어 드려요.").font(.dearby(.subheadline)).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 20).padding(.top, 24)
        }
        .listStyle(.insetGrouped)
        // An opaque grouped page also at the medium detent, where the system sheet is translucent and the title would wash out.
        .scrollContentBackground(.hidden).background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 8) {
                Button { join(candidates.filter { chosen.contains($0.id) }) } label: { Text("참여해요").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).disabled(chosen.isEmpty).accessibilityIdentifier("today-onboarding-join")
                Button(action: skip) { Text("오늘은 아니에요").frame(maxWidth: .infinity, minHeight: 44) }
                    .buttonStyle(.borderless).tint(DearbyStyle.teal).accessibilityIdentifier("today-onboarding-skip")
            }
            .frame(maxWidth: 420).frame(maxWidth: .infinity)
            .controlSize(.large).font(.dearby(.body).weight(.semibold)).tint(DearbyStyle.tealFill)
            .padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 16)
        }
    }
    private func row(_ candidate: TodayCandidate) -> some View {
        let selected = chosen.contains(candidate.id)
        return Button {
            if selected { chosen.remove(candidate.id) } else { chosen.insert(candidate.id) }
        } label: {
            HStack(spacing: 12) {
                ProgramLogo(name: candidate.activity.program ?? candidate.activity.title, url: candidate.activity.programID.map(logoURL))
                VStack(alignment: .leading, spacing: 2) {
                    Text(candidate.activity.title).font(.dearby(.headline)).foregroundStyle(.primary).lineLimit(2)
                    if let place = candidate.place {
                        Text(place).font(.dearby(.subheadline)).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Text("오늘 " + candidate.timeRange).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.teal).lineLimit(1)
                }
                Spacer(minLength: 8)
                Image(systemName: selected ? "checkmark.circle.fill" : "circle").font(.title2)
                    .foregroundStyle(selected ? AnyShapeStyle(DearbyStyle.teal) : AnyShapeStyle(.tertiary))
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore).accessibilityLabel(candidate.spokenLabel)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
        .accessibilityIdentifier("today-onboarding-\(candidate.id)")
    }
}

extension TodayCandidate {
    /// `참여해요`'s timetable (contract "오늘 행사 온보딩"): on `내 시간표`, the day when today's blocks fold into one ("하루 시간표"), else the first
    /// of today's blocks with a timetable; nil when none has one (the app opens 진행 단계 instead).
    func timetableRoute(now: Date) -> TimetableRoute? {
        guard let block = blocks.first(where: \.hasTimetable) else { return nil }
        var route = TimetableRoute(activity: activity, block: block, title: block.title.isEmpty ? "활동" : block.title, date: block.text(now: now) ?? "",
                                   now: now, favoritesFirst: true)
        if let day = route.day, day.hasTimetable {
            route = TimetableRoute(activity: activity, day: day, now: now)
            route.favoritesFirst = true
        }
        return route
    }
}
