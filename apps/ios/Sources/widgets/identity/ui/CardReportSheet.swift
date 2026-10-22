import SwiftUI

/// 신고하기 (contract "명함 신고·차단"): a reason from an inline picker and an optional description (required for 기타, at most 500).
/// `send` returns whether the server took it; the sheet closes on success and stays for another try otherwise.
struct CardReportSheet: View {
    let name: String
    let send: (CardReportReason, String?) async -> Bool
    @State private var reason = CardReportReason.spam
    @State private var detail = ""
    @State private var sending = false
    @Environment(\.dismiss) private var dismiss
    private static let limit = 500
    private var trimmed: String { detail.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var canSend: Bool { !sending && (reason != .other || !trimmed.isEmpty) && detail.count <= Self.limit }
    var body: some View {
        Form {
            Section {
                Picker("사유", selection: $reason) {
                    ForEach(CardReportReason.allCases) { Text($0.title).tag($0) }
                }.pickerStyle(.inline).labelsHidden()
            } header: { Text("사유") }
            Section {
                TextField(reason == .other ? "무엇이 문제인지 적어 주세요" : "설명(선택)", text: $detail, axis: .vertical).lineLimit(3...8)
            } header: {
                Text(reason == .other ? "설명 (필수)" : "설명")
            } footer: {
                Text("\(detail.count)/\(Self.limit) · 신고만으로 명함이 지워지지는 않아요. 차단은 더 보기 메뉴에서 해요.")
                    .foregroundStyle(detail.count > Self.limit ? .red : .secondary)
            }
        }
        .navigationTitle("\(name)님의 명함 신고").navigationBarTitleDisplayMode(.inline)
        .scrollDismissesKeyboard(.interactively)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                if sending { ProgressView() } else {
                    Button("보내기") {
                        Task {
                            sending = true
                            if await send(reason, trimmed.isEmpty ? nil : trimmed) { dismiss() }
                            sending = false
                        }
                    }.disabled(!canSend)
                }
            }
        }
    }
}
