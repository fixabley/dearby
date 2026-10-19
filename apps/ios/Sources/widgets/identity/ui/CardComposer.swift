import SwiftUI

/// 명함 제작: name the card and choose which contacts and histories it shows; the body is the built-in template
/// filled from the profile (contract "템플릿 — 2026-10-18 앱 안 꾸미기 삭제"). Sign-in, publishing and errors are the page's job.
struct CardComposer: View {
    @Binding var draft: CardDraft
    var publishing = false
    var errorMessage: String?
    let publish: () -> Void
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                section("명함") {
                    field("명함 이름 (선택)", text: $draft.cardTitle, prompt: "비워 두면 '내 명함'으로 저장해요")
                }
                section("미리보기") {
                    DearbyMarkdownText(source: draft.preview).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(DearbyStyle.line))
                    Text("프로필의 이름·직무·소개와 아래에서 고른 연락처가 명함 본문이 돼요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                }
                section("공개할 연락처") {
                    if draft.profile.contacts.isEmpty { Text("프로필에 연락처가 없어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet) }
                    ForEach(draft.profile.contacts) { contact in
                        let locked = draft.tokenContactIDs.contains(contact.id)
                        Toggle(isOn: Binding(get: { draft.effectiveContactIDs.contains(contact.id) },
                                             set: { on in if on { draft.publicContactIDs.insert(contact.id) } else { draft.publicContactIDs.remove(contact.id) } })) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(contact.label) · \(contact.value)").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink)
                                if locked { Text("본문에 넣어서 공개돼요").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet) }
                            }
                        }.tint(DearbyStyle.teal).disabled(locked).frame(minHeight: 44)
                    }
                }
                section("공개할 활동 이력") {
                    if draft.profile.histories.isEmpty { Text("프로필에 이력이 없어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet) }
                    ForEach(draft.profile.histories) { history in
                        Toggle(isOn: Binding(get: { draft.publicHistoryIDs.contains(history.id) },
                                             set: { on in if on { draft.publicHistoryIDs.insert(history.id) } else { draft.publicHistoryIDs.remove(history.id) } })) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(history.chain.joined(separator: " · ")).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.ink)
                                Text([history.roleTitle, ContactRules.period(start: history.startDate, end: history.endDate)].filter { !$0.isEmpty }.joined(separator: " · "))
                                    .font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                                if !history.isApproved {
                                    Text(history.approval == "rejected" ? "거절된 이력은 명함에 넣을 수 없어요" : "관리자 승인 뒤 명함에 넣을 수 있어요")
                                        .font(.dearby(.caption)).foregroundStyle(DearbyStyle.danger)
                                }
                            }
                        }.tint(DearbyStyle.teal).disabled(!history.isApproved).frame(minHeight: 44)
                    }
                }
            }.padding(20)
        }
        .safeAreaInset(edge: .bottom) { publishBar }
    }
    private var publishBar: some View {
        VStack(spacing: 8) {
            if let errorMessage {
                Text(errorMessage).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.danger).frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(spacing: 10) {
                if draft.changed { Button("원래대로") { draft.revert() }.buttonStyle(DearbyButtonStyle(outlined: true)).disabled(publishing) }
                Button(action: publish) {
                    if publishing { ProgressView().tint(.white) } else { Text("명함 발행") }
                }.buttonStyle(DearbyButtonStyle()).disabled(publishing).accessibilityLabel(publishing ? "명함 발행 중" : "명함 발행")
            }
            Text("발행한 명함은 바뀌지 않아요. 고치면 새 명함으로 발행돼요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
        }.padding(.horizontal, 20).padding(.vertical, 12).background(Color(.systemBackground))
            .overlay(alignment: .top) { Rectangle().fill(DearbyStyle.line).frame(height: 1) }
    }
    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink).accessibilityAddTraits(.isHeader)
            content()
        }
    }
    private func field(_ label: String, text: Binding<String>, prompt: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet).accessibilityHidden(true)
            DearbyInlineField(label: label, text: text, editing: true, prompt: prompt)
        }
    }
}
