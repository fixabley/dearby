import SwiftUI

struct HomePage<Discovery: View, Saved: View, QR: View, Wallet: View, Profile: View>: View {
    @Binding var selectedTab: Int
    let configured: Bool
    let discovery: Discovery
    let saved: Saved
    let qr: QR
    let wallet: Wallet
    let profile: Profile
    private let titles = ["발견", "저장", "QR", "받은 명함", "내 프로필"]
    private let symbols = ["safari", "bookmark", "qrcode.viewfinder", "person.text.rectangle", "person.crop.circle"]
    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack { discovery }.tag(0)
            NavigationStack { saved }.tag(1)
            NavigationStack { qr }.tag(2)
            NavigationStack { wallet }.tag(3)
            NavigationStack { profile }.tag(4)
        }
        .toolbar(.hidden, for: .tabBar)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 0) {
                Divider()
                HStack(spacing: 0) {
                    ForEach(titles.indices, id: \.self) { index in
                        Button { selectedTab = index } label: {
                            VStack(spacing: 5) {
                                Image(systemName: symbols[index]).font(.system(size: 23, weight: .regular))
                                Text(titles[index]).font(.caption2.weight(selectedTab == index ? .semibold : .regular))
                            }.frame(maxWidth: .infinity, minHeight: 57)
                                .foregroundStyle(selectedTab == index ? DearbyStyle.teal : .secondary)
                                .background(selectedTab == index ? DearbyStyle.mint : .clear, in: RoundedRectangle(cornerRadius: 10))
                        }.buttonStyle(.plain).accessibilityIdentifier("tab-\(index)")
                            .accessibilityAddTraits(selectedTab == index ? .isSelected : [])
                    }
                }.padding(.horizontal, 12).padding(.top, 8).padding(.bottom, 4)
            }.background(.white)
        }
        .tint(DearbyStyle.teal).preferredColorScheme(.light)
        .safeAreaInset(edge: .top) {
            if !configured {
                Text("서버 미설정 · 저장된 정보는 유지됩니다").font(.caption).frame(maxWidth: .infinity)
                    .padding(6).background(DearbyStyle.mint)
            }
        }
    }
}
