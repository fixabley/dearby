import SwiftUI

struct CatalogPage: View {
    let state: CatalogViewModel
    @State private var filter = 0
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                DearbyLogo(width: 96).padding(.bottom, 8)
                Text("활동 둘러보기").font(.title2.bold())
                Text("클릭형 프로토타입 · 모든 활동과 모집 상태는 예시입니다.")
                    .font(.caption).foregroundStyle(DearbyStyle.quiet)
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
                ForEach(state.activities.filter { filter == 0 || (filter == 1 ? $0.participationType == .registration : $0.participationType == .selection) }) { row($0) }
            }.padding(20)
        }
        .navigationDestination(for: String.self) { id in ActivityDetailView(state: state, activityID: id) }
        .scrollContentBackground(.hidden).background(.white)
        .tint(DearbyStyle.teal).navigationTitle("")
        .navigationBarTitleDisplayMode(.inline).toolbar(.hidden, for: .navigationBar)
    }
    private func row(_ activity: ActivityModel) -> some View {
        NavigationLink(value: activity.id) {
            HStack(alignment: .top, spacing: 12) {
                VStack(spacing: 8) {
                    Image(systemName: "photo").font(.title2)
                    Text("이미지 미제공").font(.caption2)
                }.foregroundStyle(DearbyStyle.quiet).frame(width: 104, height: 104)
                    .background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 9))
                VStack(alignment: .leading, spacing: 7) {
                    Text(activity.title).font(.headline).foregroundStyle(Color.primary)
                    Text(activity.audience).font(.caption).foregroundStyle(DearbyStyle.quiet).lineLimit(2)
                    Divider()
                    Label(activity.demoStatus, systemImage: "calendar")
                    Label(activity.dateLabel, systemImage: "mappin.and.ellipse")
                }.font(.caption).foregroundStyle(DearbyStyle.quiet).frame(maxWidth: .infinity, alignment: .leading)
            }
        }.buttonStyle(.plain).accessibilityIdentifier("activity-\(activity.id)")
            .padding(10).background(.white, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
    }
}
