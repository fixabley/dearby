import SwiftUI

/// 명함 제작: decorate the card body on the rendered view, insert profile data as tokens, and choose which contacts
/// and histories the card shows (contract "템플릿"). Sign-in, publishing and errors are the page's job.
struct CardComposer: View {
    @Binding var draft: CardDraft
    var publishing = false
    var errorMessage: String?
    let publish: () -> Void
    /// Which content version the editor, token menu and preview work on (contract "웹 버전").
    @State private var version = CardDraft.Version.mobile
    private var editedTemplate: Binding<String> {
        Binding(get: { draft.template(for: version) }, set: { draft.setTemplate($0, for: version) })
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                section("명함") {
                    field("명함 이름 (선택)", text: $draft.cardTitle, prompt: "비워 두면 '내 명함'으로 저장해요")
                }
                section("꾸미기") {
                    Toggle("웹 버전 따로 쓰기", isOn: Binding(get: { draft.hasWebVersion }, set: { on in draft.hasWebVersion = on; if !on { version = .mobile } }))
                        .font(.dearby(.subheadline)).tint(DearbyStyle.teal).frame(minHeight: 44)
                    Text("웹에서 넓은 화면(640px 초과)일 때 보이는 내용이에요. 앱은 항상 모바일 버전을 보여요.")
                        .font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                    if draft.hasWebVersion {
                        Picker("고치는 버전", selection: $version) {
                            Text("모바일").tag(CardDraft.Version.mobile)
                            Text("웹").tag(CardDraft.Version.web)
                        }.pickerStyle(.segmented)
                    }
                    TemplateEditor(template: editedTemplate, tokens: TemplateDocument.tokens(for: draft.profile), preview: draft.preview(for: version))
                        .id(version)
                    Menu {
                        ForEach(TemplateDocument.tokens(for: draft.profile)) { token in
                            Button("\(token.label)\(token.value.isEmpty ? "" : " · \(token.value)")") {
                                let current = draft.template(for: version)
                                draft.setTemplate(current + (current.hasSuffix("\n") || current.isEmpty ? "" : "\n") + token.markdown, for: version)
                            }
                        }
                    } label: {
                        Label("데이터 넣기", systemImage: "plus.circle").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal).frame(minHeight: 44)
                    }.accessibilityHint("프로필 데이터를 본문 끝에 넣어요")
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
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.white)
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
