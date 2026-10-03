import SwiftUI

struct DearbyBadge: View {
    let title: String
    var body: some View {
        Text(title).font(.caption.weight(.semibold)).foregroundStyle(DearbyStyle.teal)
            .padding(.horizontal, 10).padding(.vertical, 6).background(DearbyStyle.mint, in: Capsule())
    }
}
struct DearbyAvatar: View {
    let name: String
    var filled = false
    var size: CGFloat = 76
    var body: some View {
        Text(String(name.prefix(1))).font(.system(size: size * 0.42, weight: .bold))
            .foregroundStyle(filled ? .white : DearbyStyle.teal).frame(width: size, height: size)
            .background(filled ? DearbyStyle.teal : DearbyStyle.teal.opacity(0.12), in: Circle())
            .accessibilityHidden(true)
    }
}
struct DearbyInfoRow: View {
    let title: String
    let value: String
    var symbol: String?
    @Environment(\.dynamicTypeSize) private var size
    var body: some View {
        let layout = size.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 6))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 14))
        layout {
            HStack(spacing: 10) {
                if let symbol { Image(systemName: symbol).frame(width: 22) }
                Text(title)
            }.foregroundStyle(DearbyStyle.quiet).frame(minWidth: 92, alignment: .leading)
            Text(value).frame(maxWidth: .infinity, alignment: .leading)
        }.font(.subheadline).padding(.vertical, 4)
    }
}
struct DearbySheetHeader: View {
    let title: String
    var detail = ""
    let close: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Text(title).font(.title2.bold())
            Spacer()
            if !detail.isEmpty { Text(detail).font(.subheadline).foregroundStyle(.secondary) }
            Button(action: close) {
                Image(systemName: "xmark").font(.title3).foregroundStyle(DearbyStyle.quiet).frame(width: 44, height: 44)
            }.accessibilityLabel("닫기")
        }.padding(.leading, 20).padding(.trailing, 8).padding(.top, 20)
    }
}
struct DearbySegments: View {
    let labels: [String]
    @Binding var selection: Int
    var body: some View {
        HStack(spacing: 0) {
            ForEach(labels.indices, id: \.self) { index in
                Button { selection = index } label: {
                    Text(labels[index]).font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 44).padding(.horizontal, 4)
                        .foregroundStyle(selection == index ? .white : DearbyStyle.quiet)
                        .background(selection == index ? DearbyStyle.teal : .clear, in: RoundedRectangle(cornerRadius: 11))
                }.buttonStyle(.plain).accessibilityAddTraits(selection == index ? .isSelected : [])
            }
        }.background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 11))
    }
}
