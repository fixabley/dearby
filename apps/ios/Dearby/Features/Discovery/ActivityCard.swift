import SwiftUI

struct ActivityCard: View {
    @Environment(\.dynamicTypeSize) private var typeSize

    let notice: ActivityNotice
    let organization: ActivityOrganization?
    let contextNames: String
    let saved: Bool
    let position: String
    let compact: Bool
    let save: () -> Void
    let showDetail: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: compact ? 10 : 18) {
            VStack(alignment: .leading, spacing: compact ? 10 : 18) {
                HStack {
                    Label("공고 샘플", systemImage: "sparkle")
                        .foregroundStyle(.tint)
                    Spacer()
                    Text(position).foregroundStyle(.secondary)
                }.font(.caption.bold())
                NoticeClassificationView(category: notice.categorySummary, contextNames: contextNames)
                    .lineLimit(2)
                    .accessibilityIdentifier("classification.\(notice.id)")
                Text(notice.title)
                    .font(compact ? .title3.bold() : .title2.bold())
                    .lineLimit(3)
                if !compact && !typeSize.isAccessibilitySize {
                    Divider()
                    NoticeFact(label: "참여 대상", value: notice.audience.summary)
                    NoticeFact(label: "신청 마감", value: notice.application.summary)
                    NoticeFact(label: "활동 장소", value: notice.location.summary)
                }
                if !notice.qualityIssues.isEmpty {
                    Label("확인이 필요한 정보가 있어요", systemImage: "info.circle")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .contentShape(Rectangle())
            .onTapGesture(count: 2, perform: save)
            .accessibilityAction(named: "조직 즐겨찾기에 저장", save)
            .accessibilityIdentifier("activity.\(notice.id)")

            if let organization {
                Button(action: save) {
                    Label(saved ? "저장됨 · \(organization.name)" : "\(organization.name) 저장",
                          systemImage: saved ? "heart.fill" : "heart")
                        .frame(maxWidth: .infinity).lineLimit(2)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("save.\(notice.id)")
            } else {
                Text("저장할 조직 확인 중").font(.caption).foregroundStyle(.secondary)
            }
            Button("공고 정보 · 출처 보기", action: showDetail)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("details.\(notice.id)")
        }
        .padding(compact ? 16 : 22)
        .background(.background, in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(.quaternary))
        .padding(.horizontal, 16).padding(.vertical, 8)
    }
}
