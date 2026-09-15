import SwiftUI

struct NoticeCardScheduleList: View {
    let schedules: [NoticeCardScheduleState]
    let onOpenMap: (Int, Int) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ForEach(schedules) { schedule in
                NoticeCardScheduleRow(state: schedule) { onOpenMap(schedule.id, $0) }
            }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
