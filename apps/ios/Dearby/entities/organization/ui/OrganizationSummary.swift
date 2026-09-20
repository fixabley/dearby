import SwiftUI

/// Organization path, linked-notice availability, and count surround widget-owned navigation/actions.
struct OrganizationSummary<Content: View>: View {
    let name: String
    let ancestors: String
    let noticeCount: Int
    @ViewBuilder let content: () -> Content

    var body: some View {
        Section {
            if !ancestors.isEmpty { InformationRow(title: "상위 조직", value: ancestors, systemImage: "building.2") }
            if noticeCount == 0 { StatusMessage(text: "현재 연결된 공고가 없어요", systemImage: "doc.text") }
            content()
        } header: {
            Text(name).font(.headline).foregroundStyle(.primary).textCase(nil)
        } footer: {
            if noticeCount > 0 { Text("연결된 공고 \(noticeCount)개") }
        }
    }
}
