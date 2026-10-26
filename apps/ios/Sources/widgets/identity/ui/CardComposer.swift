import SwiftUI

/// 명함 제작 form: name the card and choose which contacts and histories it shows; the body is the profile's introduction
/// (contract "템플릿" 폐지, 2026-10-26). Sign-in, publishing and errors are the page's job.
/// The name row stacks its label over a `.title3` input (`DearbyFormRow`); contact and history choices are checkbox rows (`DearbyCheckRow`)
/// because their labels run to several lines (contract "토글 라벨은 바로 옆" 2026-10-20).
struct CardComposer: View {
    @Binding var draft: CardDraft
    /// 이 명함 고치기: the button saves over the same card (`고친 내용 저장`) instead of publishing a new one.
    var editing = false
    var publishing = false
    var errorMessage: String?
    let publish: () -> Void
    @FocusState private var field: String?
    var body: some View {
        Form {
            Section {
                DearbyFormRow(label: "명함 이름", text: $draft.cardTitle, focus: $field, value: "title")
            } footer: {
                Text("비워 두면 ‘내 명함’으로 저장해요.").font(.dearby(.subheadline))
            }
            Section {
                // 사진 넣기 (contract "프로필 사진 — 명함에 싣기"): a checkbox row, since a switch would sit alone at the row's far end.
                if draft.profile.photoUrl != nil {
                    DearbyCheckRow(isOn: $draft.includePhoto) {
                        HStack(spacing: 12) {
                            DearbyPhotoAvatar(photoUrl: draft.profile.photoUrl, name: draft.profile.name, filled: true, size: 40)
                            Text("사진 넣기")
                        }
                    }.accessibilityIdentifier("card-include-photo")
                } else {
                    Text("프로필에서 사진을 넣을 수 있어요").foregroundStyle(.secondary)
                }
            } header: {
                Text("사진").font(.dearby(.subheadline))
            }
            Section {
                DearbyMarkdownText(source: draft.preview).padding(.vertical, 4)
            } header: {
                Text("미리보기").font(.dearby(.subheadline))
            } footer: {
                Text("프로필의 자기소개가 명함 본문이 돼요.").font(.dearby(.subheadline))
            }
            Section {
                if draft.profile.contacts.isEmpty { Text("프로필에 연락처가 없어요.").foregroundStyle(.secondary) }
                ForEach(draft.profile.contacts) { contact in
                    DearbyCheckRow(isOn: Binding(get: { draft.publicContactIDs.contains(contact.id) },
                                                 set: { on in if on { draft.publicContactIDs.insert(contact.id) } else { draft.publicContactIDs.remove(contact.id) } })) {
                        HStack(spacing: 12) {
                            DearbyGlyphView(glyph: ContactModel.glyph(kind: contact.kind))
                            Text(ContactModel.username(kind: contact.kind, contact.value) ?? contact.value)
                        }
                    }
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
    private var action: String { editing ? "고친 내용 저장" : "명함 발행" }
    private var publishBar: some View {
        VStack(spacing: 8) {
            if let errorMessage { DearbyFieldError(text: errorMessage).frame(maxWidth: .infinity, alignment: .leading) }
            HStack(spacing: 10) {
                if draft.changed {
                    Button { draft.revert() } label: { Text("원래대로").frame(maxWidth: .infinity) }.buttonStyle(.bordered).disabled(publishing)
                }
                Button(action: publish) {
                    Group { if publishing { ProgressView().tint(DearbyStyle.onTeal) } else { Text(action) } }.frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent).tint(DearbyStyle.tealFill).disabled(publishing).accessibilityLabel(publishing ? "\(action) 중" : action)
            }.controlSize(.large).font(.dearby(.body).weight(.semibold))
            if editing { Text("고친 내용은 이 명함에 바로 반영돼요. QR과 링크는 그대로예요.").font(.dearby(.footnote)).foregroundStyle(.secondary) }
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.bar)
    }
}
