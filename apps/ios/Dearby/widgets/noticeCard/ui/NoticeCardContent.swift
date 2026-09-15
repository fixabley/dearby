import SwiftUI

struct NoticeCardContent: View {
    @Environment(\.dynamicTypeSize) private var typeSize

    let state: NoticeCardState
    let position: String
    let compact: Bool
    let onSave: () -> Void
    let onShowDetail: () -> Void
    var scrollSchedules = true
    var onOpenMap: (Int, Int) -> Void = { _, _ in }
    var body: some View {
        VStack(alignment: .leading, spacing: compact ? NativeSpacing.related : NativeSpacing.content) {
            VStack(alignment: .leading, spacing: compact ? NativeSpacing.related : NativeSpacing.content) {
                HStack {
                    Label("공고 샘플", systemImage: "sparkle")
                        .foregroundStyle(.tint)
                    Spacer()
                    Text(position).foregroundStyle(.secondary)
                }.font(.caption.bold())
                NoticeClassificationView(category: state.category, contextNames: state.contextNames)
                    .lineLimit(typeSize.isAccessibilitySize ? nil : 2)
                    .accessibilityIdentifier("classification.\(state.id)")
                Text(state.title)
                    .font(compact ? .title3.bold() : .title2.bold())
                    .lineLimit(typeSize.isAccessibilitySize ? nil : 3)
                Divider()
                InformationRow(title: "참여 대상", value: state.targetUser)
                if typeSize.isAccessibilitySize || !scrollSchedules {
                    NoticeCardScheduleList(schedules: state.schedules, onOpenMap: onOpenMap)
                } else {
                    ScrollView(.vertical) { NoticeCardScheduleList(schedules: state.schedules, onOpenMap: onOpenMap) }
                        .scrollBounceBehavior(.basedOnSize)
                        .frame(minHeight: 150)
                }
                if state.hasQualityIssues {
                    StatusMessage(text: "확인이 필요한 정보가 있어요")
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .contentShape(Rectangle())
            .onTapGesture(count: 2, perform: onSave)
            .accessibilityAction(named: "조직 즐겨찾기에 저장", onSave)
            .accessibilityIdentifier("activity.\(state.id)")

            if let organizationName = state.organizationName {
                NoticeCardSaveButton(saved: state.saved, organizationName: organizationName, onSave: onSave)
                    .accessibilityIdentifier("save.\(state.id)")
            } else {
                StatusMessage(text: "저장할 조직 확인 중", systemImage: "heart")
            }
            SecondaryButton(action: onShowDetail) {
                Label("공고 정보 · 출처 보기", systemImage: "info.circle").labelStyle(.iconOnly)
                    .frame(maxWidth: .infinity)
            }
            .accessibilityIdentifier("details.\(state.id)")
        }
        .padding(compact ? NativeSpacing.content : NativeSpacing.section)
        .background(NativeSurface.content, in: RoundedRectangle(cornerRadius: 24))
        .padding(.horizontal, NativeSpacing.content).padding(.vertical, NativeSpacing.related)
    }

}

#Preview("공고 · 긴 제목 · 큰 글자") {
    ScrollView {
        NoticeCardContent(state: .init(id: "preview", title: "여러 줄로 이어지는 긴 공고 제목도 축소하지 않고 읽을 수 있어요",
                                category: "교육", contextNames: "지역 기관", targetUser: "누구나", applicationSummary: "일정 확인 필요",
                                locationSummary: "장소 확인 필요", hasQualityIssues: true, organizationName: "긴 이름의 관심 조직", saved: true),
                   position: "1 / 4", compact: false, onSave: {}, onShowDetail: {})
    }
    .background(NativeSurface.canvas)
    .environment(\.dynamicTypeSize, .accessibility5)
}

