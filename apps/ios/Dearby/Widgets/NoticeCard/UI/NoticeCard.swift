import SwiftUI

struct NoticeCard: View {
    @Environment(\.dynamicTypeSize) private var typeSize

    let state: NoticeCardState
    let position: String
    let compact: Bool
    let onSave: () -> Void
    let onShowDetail: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: compact ? 10 : 18) {
            VStack(alignment: .leading, spacing: compact ? 10 : 18) {
                HStack {
                    Label("공고 샘플", systemImage: "sparkle")
                        .foregroundStyle(.tint)
                    Spacer()
                    Text(position).foregroundStyle(.secondary)
                }.font(.caption.bold())
                NoticeClassificationView(category: state.category, contextNames: state.contextNames)
                    .lineLimit(2)
                    .accessibilityIdentifier("classification.\(state.id)")
                Text(state.title)
                    .font(compact ? .title3.bold() : .title2.bold())
                    .lineLimit(3)
                if !compact && !typeSize.isAccessibilitySize {
                    Divider()
                    NoticeFact(label: "참여 대상", value: state.targetUser)
                    NoticeFact(label: "신청 마감", value: state.applicationSummary)
                    NoticeFact(label: "활동 장소", value: state.locationSummary)
                }
                if state.hasQualityIssues {
                    Label("확인이 필요한 정보가 있어요", systemImage: "info.circle")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .contentShape(Rectangle())
            .onTapGesture(count: 2, perform: onSave)
            .accessibilityAction(named: "조직 즐겨찾기에 저장", onSave)
            .accessibilityIdentifier("activity.\(state.id)")

            if let organizationName = state.organizationName {
                SaveOrganizationButton(saved: state.saved, organizationName: organizationName, onSave: onSave)
                .accessibilityIdentifier("save.\(state.id)")
            } else {
                Text("저장할 조직 확인 중").font(.caption).foregroundStyle(.secondary)
            }
            Button("공고 정보 · 출처 보기", action: onShowDetail)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("details.\(state.id)")
        }
        .padding(compact ? 16 : 22)
        .background(.background, in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(.quaternary))
        .padding(.horizontal, 16).padding(.vertical, 8)
    }
}
