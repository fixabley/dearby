import SwiftUI

/// 명함 제작 form: name the card and choose which contacts and histories it shows; the body is the built-in template
/// filled from the profile (contract "템플릿 — 2026-10-18 앱 안 꾸미기 삭제"). Sign-in, publishing and errors are the page's job.
/// The name row stacks its label over a `.title3` input (`DearbyFormRow`); contact and history choices are checkbox rows (`DearbyCheckRow`)
/// because their labels run to several lines (contract "토글 라벨은 바로 옆" 2026-10-20).
struct CardComposer: View {
    @Binding var draft: CardDraft
    var publishing = false
    var errorMessage: String?
    let publish: () -> Void
    @FocusState private var field: String?
    var body: some View {
        Form {
            Section {
                DearbyFormRow(label: "명함 이름", focus: $field, value: "title") {
                    TextField("내 명함", text: $draft.cardTitle).accessibilityLabel("명함 이름 (선택)")
                }
            } footer: {
                Text("비워 두면 ‘내 명함’으로 저장해요.").font(.dearby(.subheadline))
            }
            Section {
                DearbyMarkdownText(source: draft.preview).padding(.vertical, 4)
            } header: {
                Text("미리보기").font(.dearby(.subheadline))
            } footer: {
                Text("프로필의 이름·직무·소개와 아래에서 고른 연락처가 명함 본문이 돼요.").font(.dearby(.subheadline))
            }
            Section {
                if draft.profile.contacts.isEmpty { Text("프로필에 연락처가 없어요.").foregroundStyle(.secondary) }
                ForEach(draft.profile.contacts) { contact in
                    let locked = draft.tokenContactIDs.contains(contact.id)
                    DearbyCheckRow(isOn: Binding(get: { draft.effectiveContactIDs.contains(contact.id) },
                                                 set: { on in if on { draft.publicContactIDs.insert(contact.id) } else { draft.publicContactIDs.remove(contact.id) } })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(contact.label) · \(contact.value)")
                            if locked { Text("본문에 넣어서 공개돼요").font(.dearby(.footnote)).foregroundStyle(.secondary) }
                        }
                    }.disabled(locked)
                }
            } header: {
                Text("공개할 연락처").font(.dearby(.subheadline))
            }
            Section {
                if draft.profile.histories.isEmpty { Text("프로필에 이력이 없어요.").foregroundStyle(.secondary) }
                ForEach(draft.profile.histories) { history in
                    DearbyCheckRow(isOn: Binding(get: { draft.publicHistoryIDs.contains(history.id) },
                                                 set: { on in if on { draft.publicHistoryIDs.insert(history.id) } else { draft.publicHistoryIDs.remove(history.id) } })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(history.chain.joined(separator: " · ")).fontWeight(.semibold)
                            Text([history.roleTitle, ContactRules.period(start: history.startDate, end: history.endDate)].filter { !$0.isEmpty }.joined(separator: " · "))
                                .font(.dearby(.footnote)).foregroundStyle(.secondary)
                            if !history.isApproved {
                                Text(history.approval == "rejected" ? "거절된 이력은 명함에 넣을 수 없어요" : "관리자 승인 뒤 명함에 넣을 수 있어요")
                                    .font(.dearby(.footnote)).foregroundStyle(DearbyStyle.danger)
                            }
                        }
                    }.disabled(!history.isApproved)
                }
            } header: {
                Text("공개할 활동 이력").font(.dearby(.subheadline))
            }
        }
        .font(.dearby(.body)).dearbyDismissKeyboard { field = nil }
        .safeAreaInset(edge: .bottom) { publishBar }
    }
    private var publishBar: some View {
        VStack(spacing: 8) {
            if let errorMessage { DearbyFieldError(text: errorMessage).frame(maxWidth: .infinity, alignment: .leading) }
            HStack(spacing: 10) {
                if draft.changed {
                    Button { draft.revert() } label: { Text("원래대로").frame(maxWidth: .infinity) }.buttonStyle(.bordered).disabled(publishing)
                }
                Button(action: publish) {
                    Group { if publishing { ProgressView().tint(DearbyStyle.onTeal) } else { Text("명함 발행") } }.frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent).tint(DearbyStyle.tealFill).disabled(publishing).accessibilityLabel(publishing ? "명함 발행 중" : "명함 발행")
            }.controlSize(.large).font(.dearby(.body).weight(.semibold))
            Text("발행한 명함은 바뀌지 않아요. 고치면 새 명함으로 발행돼요.").font(.dearby(.footnote)).foregroundStyle(.secondary)
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.bar)
    }
}
