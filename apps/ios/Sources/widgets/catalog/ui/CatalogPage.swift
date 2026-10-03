import SwiftUI

struct CatalogPage: View {
    let state: CatalogViewModel
    var saved = false
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
                HStack { Text(saved ? "저장한 활동" : "활동 둘러보기").font(.title2.bold()); DearbyBadge(title: "예시") }
                if saved && state.savedIDs.isEmpty {
                    ContentUnavailableView("저장한 활동이 없어요", systemImage: "bookmark", description: Text("북마크를 누르면 이번 실행 동안 여기에 모아 볼 수 있어요."))
                }
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
                ForEach(state.activities.filter { (!saved || state.savedIDs.contains($0.id)) && (filter == 0 || (filter == 1 ? $0.participationType == .registration : $0.participationType == .selection)) }) { row($0) }
            }.padding(20)
        }
        .alert("예시 알림", isPresented: $showNotice) { Button("확인") {} } message: { Text("새로운 알림이 없어요. 실제 푸시 알림을 사용하지 않습니다.") }
        .navigationDestination(for: String.self) { id in ActivityDetailView(state: state, activityID: id) }
        .scrollContentBackground(.hidden).background(.white)
        .tint(DearbyStyle.teal).navigationTitle("")
        .navigationBarTitleDisplayMode(.inline).toolbar(.hidden, for: .navigationBar)
    }
    private func row(_ activity: ActivityModel) -> some View {
        HStack(alignment: .top, spacing: 4) {
            NavigationLink(value: activity.id) {
                HStack(alignment: .top, spacing: 12) {
                    ActivityArtwork(activityID: activity.id).frame(width: 104, height: 112).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 9))
                    VStack(alignment: .leading, spacing: 7) {
                        Text(activity.title).font(.headline).foregroundStyle(Color.primary)
                        Text(activity.audience).font(.caption).foregroundStyle(DearbyStyle.quiet).lineLimit(2)
                        Divider()
                        Label(activity.demoStatus, systemImage: "calendar")
                        Label(activity.dateLabel + " · " + activity.location, systemImage: "mappin.and.ellipse")
                    }.font(.caption).foregroundStyle(DearbyStyle.quiet).frame(maxWidth: .infinity, alignment: .leading)
                }
            }.buttonStyle(.plain).accessibilityIdentifier("activity-\(activity.id)")
            Button { state.toggleSaved(activity.id) } label: {
                Image(systemName: state.savedIDs.contains(activity.id) ? "bookmark.fill" : "bookmark").font(.title3).frame(width: 32, height: 44)
            }.buttonStyle(.plain).accessibilityLabel("활동 저장").accessibilityIdentifier("save-\(activity.id)")
        }
            .padding(10).background(.white, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
    }
}
