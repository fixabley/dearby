import SwiftUI

struct CalendarConflictView: View {
    @State private var state: CalendarConflictState
    @State private var detent = PresentationDetent.large
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.openURL) private var openURL
    init(schedules: [ActivityScheduleModel]) {
        _state = State(initialValue: CalendarConflictState(schedules: schedules))
    }
    var body: some View {
        Group {
            if state.compared { result } else { selection }
        }
        .presentationDetents([.height(690), .large], selection: $detent)
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .onChange(of: state.compared) { _, compared in
            detent = compared && !typeSize.isAccessibilitySize ? .height(690) : .large
        }
    }
    private var selection: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("이 활동 일정과 내 캘린더 일정이 겹치는지 기기 안에서만 비교해요. 일정을 저장하거나 보내지 않고, 캘린더에 쓰지 않아요.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    if state.phase == .denied {
                        Text("캘린더 접근이 꺼져 있어요").font(.headline)
                        Text("설정에서 Dearby의 캘린더 접근을 켜면 겹치는 시간을 확인할 수 있어요. 신청은 그대로 할 수 있어요.")
                            .font(.subheadline)
                        Button("설정 열기") { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } }
                            .buttonStyle(DearbyButtonStyle(outlined: true))
                    } else {
                        Button { Task { await state.compare() } } label: {
                            if state.phase == .checking { ProgressView().tint(.white) } else { Text("내 캘린더로 확인") }
                        }.buttonStyle(DearbyButtonStyle()).disabled(state.phase == .checking)
                    }
                }.padding(20)
            }
            .navigationTitle("겹치는 시간 확인하기").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
    }
    private var result: some View {
        VStack(spacing: 0) {
            DearbySheetHeader(title: "겹치는 시간", detail: state.overlaps.isEmpty ? "0 / 0" : "\(state.index + 1) / \(state.overlaps.count)") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if state.overlaps.indices.contains(state.index) {
                        let item = state.overlaps[state.index]
                        Label(item.start.formatted(item.dateFormat), systemImage: "calendar")
                            .font(.subheadline)
                        Text("\(item.minutes)분이 겹쳐요").font(.largeTitle.bold())
                            .accessibilityIdentifier("overlap-duration")
                        Text("\(item.time(item.start)) – \(item.time(item.end))에 다른 일정과 겹쳐요.")
                            .font(.subheadline)
                        CalendarTimelineView(item: item).padding(.top, 4)
                    } else {
                        Label("내 캘린더와 겹치는 시간이 없어요", systemImage: "checkmark.circle")
                            .font(.title3.bold()).padding(.vertical, 32)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading).padding(20)
            }
            VStack(spacing: 12) {
                Label("기기 안에서만 비교했고 일정을 저장하거나 보내지 않아요.", systemImage: "info.circle")
                    .font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
                Button("확인했어요") {
                    if !state.advance() { dismiss() }
                }.buttonStyle(DearbyButtonStyle())
                if state.index + 1 < state.overlaps.count {
                    Text("다음 겹치는 일정으로 이동해요.").font(.caption).foregroundStyle(DearbyStyle.teal)
                }
            }.padding(.horizontal, 20).padding(.bottom, 16)
        }.background(.white)
    }
}
