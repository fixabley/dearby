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
            DearbyAvatar(name: name, filled: isFullProfile, size: 82)
            VStack(alignment: .leading, spacing: 7) {
                Text(name.isEmpty ? "내 이름을 입력해 주세요" : name).font(.title2.bold())
                if !job.isEmpty { Text(job).font(.subheadline).foregroundStyle(DearbyStyle.quiet) }
                if !introduction.isEmpty { Text(introduction).font(.subheadline) }
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
                        Text(item.title).font(.subheadline.weight(.semibold))
                            .padding(.bottom, 10).frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        VStack(alignment: .leading, spacing: 5) {
                            Text(item.startDate + (item.endDate.map { " – " + $0 } ?? ""))
                                .font(.caption).foregroundStyle(DearbyStyle.quiet)
                            Text(item.title).font(.headline)
                            Text(item.role).font(.subheadline).foregroundStyle(DearbyStyle.quiet)
                        }.padding(.bottom, 22).frame(maxWidth: .infinity, alignment: .leading)
                    }
                }.fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

struct ContactIcons: View {
    let contacts: [ContactModel]
    let action: (ContactModel) -> Void
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 75), alignment: .center)], spacing: 16) {
            ForEach(contacts) { contact in
                Button { action(contact) } label: {
                    VStack(spacing: 9) {
                        ContactSymbol(contact: contact).foregroundStyle(DearbyStyle.teal)
                        Text(contact.displayLabel).font(.caption).foregroundStyle(.primary)
                    }.frame(maxWidth: .infinity, minHeight: 68)
                }.buttonStyle(.plain).accessibilityLabel(contact.displayLabel + ": " + contact.value)
            }
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
