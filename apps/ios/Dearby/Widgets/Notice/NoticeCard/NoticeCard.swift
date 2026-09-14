import SwiftUI

struct NoticeCard: View {
    @Environment(\.dynamicTypeSize) private var typeSize

    let state: NoticeCardState
    let position: String
    let compact: Bool
    let onSave: () -> Void
    let onShowDetail: () -> Void
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
                if !compact && !typeSize.isAccessibilitySize {
                    Divider()
                    InformationRow(title: "참여 대상", value: state.targetUser)
                    InformationRow(title: "신청 마감", value: state.applicationSummary)
                    InformationRow(title: "활동 장소", value: state.locationSummary)
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
                Text("공고 정보 · 출처 보기")
                    .frame(maxWidth: .infinity)
            }
            .accessibilityIdentifier("details.\(state.id)")
        }
        .padding(compact ? NativeSpacing.content : NativeSpacing.section)
        .background(NativeSurface.content, in: RoundedRectangle(cornerRadius: 24))
        .padding(.horizontal, NativeSpacing.content).padding(.vertical, NativeSpacing.related)
    }
}
