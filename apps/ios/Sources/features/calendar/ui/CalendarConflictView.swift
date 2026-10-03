import SwiftUI

struct CalendarConflictView: View {
    @State private var state: CalendarConflictState
    @State private var detent = PresentationDetent.large
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var typeSize
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
                VStack(alignment: .leading, spacing: 20) {
                    Text("예시 바쁜 시간만 비교해요. 실제 캘린더를 읽거나 권한을 요청하지 않아요.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("비교할 캘린더").font(.headline)
                        Toggle("예시 캘린더", isOn: $state.selected).frame(minHeight: 44)
                        Text("2026년 10월 24일 14:00~15:00 · Asia/Seoul")
                            .font(.caption).foregroundStyle(.secondary)
                        if !state.selected { Text("비교할 캘린더를 하나 이상 선택해 주세요.").font(.caption) }
                        Button("선택한 캘린더로 확인") { state.compare() }
                            .buttonStyle(DearbyButtonStyle()).disabled(!state.selected)
                    }
                }.padding(20)
            }
            .navigationTitle("겹치는 시간 확인하기").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
    }
    private var result: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Text("겹치는 시간").font(.title2.bold())
                Spacer()
                Text(state.overlaps.isEmpty ? "0 / 0" : "\(state.index + 1) / \(state.overlaps.count)")
                    .font(.subheadline).foregroundStyle(.secondary)
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.title3).foregroundStyle(.secondary).frame(width: 44, height: 44)
                }.accessibilityLabel("닫기")
            }.padding(.leading, 20).padding(.trailing, 8).padding(.top, 20)
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
                        Label("예시 캘린더와 겹치는 시간이 없어요", systemImage: "checkmark.circle")
                            .font(.title3.bold()).padding(.vertical, 32)
                        Text("고정 예시 기준이며 실제 개인 일정과는 무관해요.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading).padding(20)
            }
            VStack(spacing: 12) {
                Label("예시 일정 · 실제 캘린더를 읽거나 변경하지 않아요.", systemImage: "info.circle")
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
