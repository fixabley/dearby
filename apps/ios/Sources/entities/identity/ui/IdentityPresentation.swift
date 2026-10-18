import SwiftUI

struct IdentityHeading: View {
    let name: String
    let job: String
    let introduction: String
    var isFullProfile = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    private var layout: AnyLayout {
        dynamicTypeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 18))
            : AnyLayout(HStackLayout(alignment: .center, spacing: 18))
    }
    var body: some View {
        layout {
            DearbyAvatar(name: String(name.suffix(2)), filled: isFullProfile, size: 82)
            VStack(alignment: .leading, spacing: 7) {
                Text(name.isEmpty ? "내 이름을 입력해 주세요" : name).font(.title2.bold())
                if !introduction.isEmpty { Text(introduction).font(.subheadline) }
                if !job.isEmpty { Text(job).font(.subheadline).foregroundStyle(DearbyStyle.quiet) }
            }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct HistoryTimeline: View {
    let histories: [HistoryModel]
    var compact = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(histories) { item in
                HStack(alignment: .top, spacing: 18) {
                    VStack(spacing: 0) {
                        Circle().fill(DearbyStyle.teal).frame(width: 9, height: 9)
                        Rectangle().fill(DearbyStyle.line).frame(width: 1)
                    }.padding(.top, 5)
                    if compact && !dynamicTypeSize.isAccessibilitySize {
                        Text(item.startDate.prefix(7).replacingOccurrences(of: "-", with: "."))
                            .font(.caption).foregroundStyle(DearbyStyle.quiet).frame(width: 64, alignment: .leading)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title).font(.subheadline.weight(.semibold))
                            badge(item)
                        }.padding(.bottom, 10).frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        VStack(alignment: .leading, spacing: 5) {
                            Text(item.startDate + (item.endDate.map { " – " + $0 } ?? ""))
                                .font(.caption).foregroundStyle(DearbyStyle.quiet)
                            Text(item.title).font(.headline)
                            HStack(spacing: 8) {
                                Text(item.role).font(.subheadline).foregroundStyle(DearbyStyle.quiet)
                                badge(item)
                            }
                        }.padding(.bottom, 22).frame(maxWidth: .infinity, alignment: .leading)
                    }
                }.fixedSize(horizontal: false, vertical: true)
            }
        }
    }
    private func badge(_ item: HistoryModel) -> some View { HistoryBadge(verified: item.verified) }
}

/// 미확인: no admin-verified evidence yet (contract "이력"). Verified entries get a small check.
struct HistoryBadge: View {
    let verified: Bool
    var body: some View {
        if verified {
            Label("확인됨", systemImage: "checkmark.seal").font(.dearby(.caption2).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
        } else {
            Text("미확인").font(.dearby(.caption2).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                .padding(.horizontal, 7).padding(.vertical, 2).background(DearbyStyle.muted, in: Capsule())
                .accessibilityLabel("미확인, 인증자료가 확인되지 않은 이력")
        }
    }
}

struct ContactSymbol: View {
    let contact: ContactModel
    var size: CGFloat = 28
    var body: some View {
        Group {
            if contact.kind == "github" {
                Image("GitHubMark").resizable().scaledToFit().frame(width: size, height: size)
            } else if contact.kind == "behance" {
                Text("Bē").font(.system(size: size, weight: .bold)).fixedSize()
            } else {
                Image(systemName: contact.symbol).font(.system(size: size))
            }
        }.accessibilityHidden(true)
    }
}
