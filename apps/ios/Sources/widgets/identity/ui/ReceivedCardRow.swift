import SwiftUI

/// One received card in the indexed list (contract "받은 명함 목록 표시"): initial avatar, name, job, the organization chip,
/// the program it was exchanged at (contract "명함 교환") and the recall badge (`맞교환` or `N일 뒤 사라져요`) at the trailing edge.
struct ReceivedCardRow: View {
    let name: String
    let job: String
    var organization: String?
    var program: String?
    /// `맞교환`, `3일 뒤 사라져요`, or empty for none.
    var badge = ""
    let open: () -> Void
    var body: some View {
        Button(action: open) {
            HStack(spacing: 12) {
                DearbyAvatar(name: name, size: 44)
                VStack(alignment: .leading, spacing: 4) {
                    Text(name).font(.dearby(.headline).weight(.bold)).foregroundStyle(DearbyStyle.ink)
                    HStack(spacing: 6) {
                        if !job.isEmpty { Text(job).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).lineLimit(1) }
                        if let organization, !organization.isEmpty { chip(organization) }
                        if let program, !program.isEmpty { chip(program) }
                    }
                }
                Spacer(minLength: 0)
                if !badge.isEmpty { DearbyBadge(title: badge, quiet: badge != "맞교환") }
            }.padding(.vertical, 10).padding(.horizontal, 12).frame(minHeight: 44).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityElement(children: .combine)
    }
    private func chip(_ text: String) -> some View {
        Text(text).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.teal).lineLimit(1)
            .padding(.horizontal, 8).padding(.vertical, 3).background(DearbyStyle.mint, in: Capsule())
    }
}

#if DEBUG
/// 미리보기용 받은 명함 목록 예시.
struct ReceivedCardListSample: View {
    var body: some View {
        VStack(spacing: 0) {
            ReceivedCardRow(name: "이서연", job: "프로덕트 디자이너", organization: "Dearby", program: "부스트캠프", badge: "맞교환") {}
            Divider()
            ReceivedCardRow(name: "박준호", job: "백엔드 개발", badge: "12일 뒤 사라져요") {}
            Divider()
            ReceivedCardRow(name: "최유나", job: "커뮤니티 매니저", organization: "Let'Swift 준비위원회") {}
        }.padding(.vertical, 20).background(Color(.systemBackground))
    }
}

#Preview("받은 명함 목록") { ReceivedCardListSample() }
#endif
