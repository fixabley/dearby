import SwiftUI

/// Notice summary, readable schedule area, and native card surface; actions are injected by the widget.
struct NoticeCardBody<Schedules: View, Actions: View>: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    let state: NoticeCardSummaryState
    let position: String
    let compact: Bool
    let scrollSchedules: Bool
    let onSave: () -> Void
    @ViewBuilder let schedules: () -> Schedules
    @ViewBuilder let actions: () -> Actions

    var body: some View {
        VStack(alignment: .leading, spacing: compact ? NativeSpacing.related : NativeSpacing.content) {
            VStack(alignment: .leading, spacing: compact ? NativeSpacing.related : NativeSpacing.content) {
                HStack {
                    Label("공고 샘플", systemImage: "sparkle").foregroundStyle(.tint)
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
                    schedules()
                } else {
                    ScrollView(.vertical) { schedules() }
                        .scrollBounceBehavior(.basedOnSize).frame(minHeight: 150)
                }
                if state.hasQualityIssues { StatusMessage(text: "확인이 필요한 정보가 있어요") }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .contentShape(Rectangle())
            .onTapGesture(count: 2, perform: onSave)
            .accessibilityAction(named: "조직 즐겨찾기에 저장", onSave)
            .accessibilityIdentifier("activity.\(state.id)")
            actions()
        }
        .padding(compact ? NativeSpacing.content : NativeSpacing.section)
        .background(NativeSurface.content, in: RoundedRectangle(cornerRadius: 24))
        .padding(.horizontal, NativeSpacing.content).padding(.vertical, NativeSpacing.related)
    }
}
