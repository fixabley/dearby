import SwiftUI

struct NoticeCardContent: View {
    let state: NoticeCardState
    let position: String
    let compact: Bool
    let onSave: () -> Void
    let onShowDetail: () -> Void
    var scrollSchedules = true
    var onOpenMap: (Int, Int) -> Void = { _, _ in }

    var body: some View {
        NoticeCardBody(state: state.summary, position: position, compact: compact,
            scrollSchedules: scrollSchedules, onSave: onSave) {
            NoticeCardScheduleList(schedules: state.schedules, onOpenMap: onOpenMap)
        } actions: {
            NoticeCardSaveButton(saved: state.saved, organizationName: state.organizationName, onSave: onSave)
                .accessibilityIdentifier("save.\(state.id)")
            NoticeDetailsButton(noticeID: state.id, onShowDetail: onShowDetail)
        }
    }
}

#Preview("공고 · 긴 제목 · 큰 글자") {
    ScrollView {
        NoticeCardContent(state: .init(id: "preview", title: "여러 줄로 이어지는 긴 공고 제목도 축소하지 않고 읽을 수 있어요",
                                category: "교육", contextNames: "지역 기관", targetUser: "누구나", applicationSummary: "일정 확인 필요",
                                locationSummary: "장소 확인 필요", hasQualityIssues: true, organizationName: "긴 이름의 관심 조직", saved: true),
                   position: "1 / 4", compact: false, onSave: {}, onShowDetail: {})
    }
    .background(Color(uiColor: .systemGroupedBackground))
    .environment(\.dynamicTypeSize, .accessibility5)
}
