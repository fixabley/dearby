import SwiftUI

struct NoticeSourceSection: View {
    let qualityIssues: [String]
    let sourceURL: URL?

    var body: some View {
        Section {
            ForEach(Array(qualityIssues.enumerated()), id: \.offset) { _, issue in StatusMessage(text: issue) }
            if let sourceURL {
                Link(destination: sourceURL) {
                    Label("원문 공고 열기", systemImage: "arrow.up.right.square").labelStyle(.iconOnly)
                        .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                }
            }
        } header: {
            Text("출처와 확인 사항")
        } footer: {
            Text("원문을 검토해 만든 샘플입니다. 현재 모집 여부와 변경된 조건은 원문에서 확인해 주세요.")
        }
    }
}
