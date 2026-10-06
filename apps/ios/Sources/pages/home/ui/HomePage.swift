import SwiftUI

struct HomePage<Discovery: View, Mine: View, QR: View, Wallet: View, Profile: View>: View {
    @Binding var selectedTab: Int
    let discovery: Discovery
    let mine: Mine
    let qr: QR
    let wallet: Wallet
    let profile: Profile
    @State private var discoveryPath: [String] = []
    @State private var minePath: [String] = []
    private var showsTabs: Bool { selectedTab > 1 || (selectedTab == 0 ? discoveryPath.isEmpty : minePath.isEmpty) }
    private let titles = ["발견", "내 활동", "QR", "받은 명함", "내 프로필"]
    private let symbols = ["safari", "calendar.badge.checkmark", "qrcode.viewfinder", "person.text.rectangle", "person.crop.circle"]
    var body: some View {
        VStack(spacing: 0) {
            Group {
                switch selectedTab {
                case 0: NavigationStack(path: $discoveryPath) { discovery }
                case 1: NavigationStack(path: $minePath) { mine }
                case 2: NavigationStack { qr }
                case 3: NavigationStack { wallet }
                default: NavigationStack { profile }
                }
            }.frame(maxWidth: .infinity, maxHeight: .infinity)
            if showsTabs {
            VStack(spacing: 0) {
                Divider()
                HStack(spacing: 0) {
                    ForEach(titles.indices, id: \.self) { index in
                        Button { selectedTab = index } label: {
                            VStack(spacing: 5) {
                                Image(systemName: symbols[index]).font(.system(size: 23, weight: .regular))
                                Text(titles[index]).font(.caption2.weight(selectedTab == index ? .semibold : .regular))
                                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                            }.frame(maxWidth: .infinity, minHeight: 57)
                                .foregroundStyle(selectedTab == index ? DearbyStyle.teal : DearbyStyle.quiet)
                                .background(selectedTab == index ? DearbyStyle.mint : .clear, in: RoundedRectangle(cornerRadius: 10))
                        }.buttonStyle(.plain).accessibilityIdentifier("tab-\(index)")
                            .accessibilityAddTraits(selectedTab == index ? .isSelected : [])
                            .accessibilityShowsLargeContentViewer { Label(titles[index], systemImage: symbols[index]) }
                    }
                }.padding(.horizontal, 12).padding(.top, 8).padding(.bottom, 4)
            }.background(.white)
            }
        }
        .tint(DearbyStyle.teal).foregroundStyle(DearbyStyle.ink).preferredColorScheme(.light)
    }
}
