import SwiftUI

struct CalendarExportPresentation<Content: View>: View {
    @State private var request: CalendarEditorRequest?
    @State private var failed = false
    @ViewBuilder let content: (@escaping (CalendarEventDraft) -> Void) -> Content

    var body: some View {
        content(open)
            .sheet(item: $request) { item in
                CalendarEventEditor(request: item, onDismiss: { request = nil })
            }
            .alert("캘린더를 열지 못했어요", isPresented: $failed) {
                Button("확인", role: .cancel) {}
            } message: { Text("날짜를 확인한 후 다시 시도해 주세요.") }
    }

    private func open(_ draft: CalendarEventDraft) {
        CalendarEditorRequest.prepare(draft, present: { request = $0 }, onFailure: { failed = true })
    }
}
