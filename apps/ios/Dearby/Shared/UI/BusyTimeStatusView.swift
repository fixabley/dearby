import SwiftUI

struct BusyTimeStatusView: View {
    let display: BusyTimeDisplay
    let timeZone: TimeZone
    let onRetry: () -> Void
    var body: some View {
        switch display.status {
        case .hidden: EmptyView()
        case .loading: ProgressView("선택 날짜의 바쁜 시간 확인 중").font(.footnote)
        case .failed:
            VStack(alignment: .leading) {
                Label("바쁜 시간을 불러오지 못했습니다. 일정 없음으로 판단할 수 없습니다.", systemImage: "exclamationmark.triangle")
                Button("다시 조회", action: onRetry).frame(minHeight: 44)
            }.font(.footnote)
        case .ready:
            VStack(alignment: .leading, spacing: NativeSpacing.compact) {
                Label(display.overlaps.isEmpty ? "선택 날짜의 활동 시간과 겹침 없음" : "선택 날짜의 활동 시간과 겹침 있음",
                      systemImage: display.overlaps.isEmpty ? "checkmark.circle" : "exclamationmark.triangle")
                ForEach(Array(display.overlaps.enumerated()), id: \.offset) { _, interval in
                    Text("겹치는 시간 · \(interval.description(in: timeZone))")
                }
                Text("기기 일정 기준 · 참여 가능을 보장하지 않습니다.")
                    .foregroundStyle(.secondary)
                if !display.intervals.isEmpty {
                    BusyTimeSummaryView(display: display, timeZone: timeZone)
                    Text("강조색: 활동 · 청록색: 바쁜 시간 · 점선: 겹치는 시간")
                        .foregroundStyle(.secondary)
                }
            }.font(.footnote).fixedSize(horizontal: false, vertical: true)
        }
    }
}
