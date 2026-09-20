import SwiftUI

struct NoticeDetailPage: View {
    let viewModel: NoticeDetailViewModel
    let preferences: CalendarPreferences

    var body: some View {
        List {
            NoticeDetail(viewModel: viewModel, preferences: preferences)
        }
        .listStyle(.insetGrouped)
        .navigationTitle("공고 정보")
        .presentationDragIndicator(.visible)
    }
}
