import SwiftUI

struct CatalogPage: View {
    let state: CatalogViewModel
    @Binding var path: [String]
    @State private var showNotice = false
    @State private var filter = 0
    private var visible: [ActivityModel] {
        state.discoverable.filter { filter == 0 || (filter == 1 ? $0.participationType == .registration : $0.participationType == .selection) }
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    DearbyLogo(width: 96)
                    Spacer()
                    Button { showNotice = true } label: { Image(systemName: "bell").font(.title2).frame(width: 44, height: 44) }
                        .accessibilityLabel("알림")
                }.padding(.bottom, 8)
                Text("활동 둘러보기").font(.title2.bold())
                ScrollView(.horizontal) {
                    HStack(spacing: 8) {
                        ForEach(Array(["전체", "바로 신청", "선발형"].enumerated()), id: \.offset) { index, title in
                            Button { filter = index } label: {
                                Text(title).font(.subheadline.weight(.semibold)).padding(.horizontal, 20).frame(minHeight: 44)
                                    .background(filter == index ? DearbyStyle.teal : DearbyStyle.muted, in: Capsule())
                                    .foregroundStyle(filter == index ? .white : DearbyStyle.quiet)
                            }.buttonStyle(.plain).accessibilityAddTraits(filter == index ? .isSelected : [])
                        }
                    }
                }.scrollIndicators(.hidden)
                switch state.phase {
                case .loading:
                    ProgressView("활동을 불러오는 중이에요").frame(maxWidth: .infinity).padding(.vertical, 40)
                case .failed:
                    ContentUnavailableView {
                        Label("활동을 불러오지 못했어요", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text("연결을 확인한 뒤 다시 시도해 주세요.")
                    } actions: {
                        Button("다시 시도") { Task { await state.load() } }.accessibilityIdentifier("catalog-retry")
                    }
                case .loaded where visible.isEmpty:
                    ContentUnavailableView("모집 중인 활동이 없어요", systemImage: "calendar",
                                           description: Text("새 활동이 공개되면 여기에서 볼 수 있어요."))
                case .loaded:
                    ForEach(visible) { activity in
                        ActivityCard(activityID: activity.id, title: activity.title, organization: activity.organization ?? "주최 정보 미확인",
                            isSelection: activity.participationType == .selection, summary: activity.summary,
                            status: activity.statusLabel(at: state.loadedAt) + " · " + (activity.participationType == .selection ? "선발형" : "바로 신청"),
                            dateAndPlace: activity.dateLabel + " · " + (activity.location ?? "장소 미확인"),
                            applyURL: activity.quickApplyURL(at: state.loadedAt), recruitmentEnd: activity.recruitmentEnd) {
                            path.append(activity.id)
                        }
                    }
                }
            }.padding(20)
        }
        .task { if state.phase == .loading && state.activities.isEmpty { await state.load() } }
        .alert("알림", isPresented: $showNotice) { Button("확인") {} } message: { Text("새로운 알림이 없어요.") }
        .navigationDestination(for: String.self) { id in ActivityDetailView(state: state, activityID: id) }
        .scrollContentBackground(.hidden).background(.white)
        .tint(DearbyStyle.teal).navigationTitle("")
        .navigationBarTitleDisplayMode(.inline).toolbar(.hidden, for: .navigationBar)
    }
}
