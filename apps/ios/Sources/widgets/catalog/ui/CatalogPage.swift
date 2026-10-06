import SwiftUI

struct CatalogPage: View {
    let state: CatalogViewModel
    @State private var showNotice = false
    @State private var filter = 0
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    DearbyLogo(width: 96)
                    Spacer()
                    Button { showNotice = true } label: { Image(systemName: "bell").font(.title2).frame(width: 44, height: 44) }
                        .accessibilityLabel("알림")
                }.padding(.bottom, 8)
                HStack { Text("활동 둘러보기").font(.title2.bold()); DearbyBadge(title: "예시") }
                ScrollView(.horizontal) {
                    HStack(spacing: 8) {
                        ForEach(Array(["전체", "참가등록형", "선발형"].enumerated()), id: \.offset) { index, title in
                            Button { filter = index } label: {
                                Text(title).font(.subheadline.weight(.semibold)).padding(.horizontal, 20).frame(minHeight: 44)
                                    .background(filter == index ? DearbyStyle.teal : DearbyStyle.muted, in: Capsule())
                                    .foregroundStyle(filter == index ? .white : DearbyStyle.quiet)
                            }.buttonStyle(.plain).accessibilityAddTraits(filter == index ? .isSelected : [])
                        }
                    }
                }.scrollIndicators(.hidden)
                ForEach(state.activities.filter { filter == 0 || (filter == 1 ? $0.participationType == .registration : $0.participationType == .selection) }) {
                    ActivityRow(activity: $0)
                }
            }.padding(20)
        }
        .alert("예시 알림", isPresented: $showNotice) { Button("확인") {} } message: { Text("새로운 알림이 없어요. 실제 푸시 알림을 사용하지 않습니다.") }
        .navigationDestination(for: String.self) { id in ActivityDetailView(state: state, activityID: id) }
        .scrollContentBackground(.hidden).background(.white)
        .tint(DearbyStyle.teal).navigationTitle("")
        .navigationBarTitleDisplayMode(.inline).toolbar(.hidden, for: .navigationBar)
    }
}
