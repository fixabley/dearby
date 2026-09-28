import SwiftUI

struct CardComposer: View {
    let profile: ProfileModel
    let publish: (CardRequest) async throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var description = ""
    @State private var contacts: Set<String> = []
    @State private var histories: Set<String> = []
    @State private var error: String?
    @State private var notice: String?
    @State private var saving = false
    @State private var preview = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    IdentityHeading(name: profile.name, job: profile.job, introduction: profile.introduction)
                    Text("이름 · 직무 · 소개는 공개됩니다.").font(.caption).foregroundStyle(DearbyStyle.quiet)
                    VStack(spacing: 12) {
                        TextField("명함 이름", text: $name)
                        TextField("설명", text: $description, axis: .vertical)
                    }.textFieldStyle(.roundedBorder)
                    VStack(alignment: .leading, spacing: 12) {
                        Text("공개할 연락처").font(.title3.bold())
                        Text("눌러서 공유할 연락처를 골라 주세요.").font(.subheadline).foregroundStyle(DearbyStyle.quiet)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 66))], spacing: 14) {
                            ForEach(profile.contacts) { item in
                                Button {
                                    if contacts.contains(item.id) { contacts.remove(item.id); notice = "\(item.displayLabel)을 숨겼습니다." } else { contacts.insert(item.id); notice = "\(item.displayLabel)을 공개합니다." }
                                } label: {
                                    VStack(spacing: 10) {
                                        ContactSymbol(contact: item)
                                            .overlay { if !contacts.contains(item.id) { Rectangle().frame(width: 40, height: 2).rotationEffect(.degrees(-45)) } }
                                        Text(item.displayLabel).font(.caption)
                                    }.frame(maxWidth: .infinity, minHeight: 70)
                                        .foregroundStyle(contacts.contains(item.id) ? DearbyStyle.teal : DearbyStyle.quiet)
                                }.buttonStyle(.plain).accessibilityValue(contacts.contains(item.id) ? "공개" : "숨김")
                                    .accessibilityAddTraits(contacts.contains(item.id) ? .isSelected : [])
                            }
                        }
                    }
                    Divider()
                    HStack {
                        Text("활동 이력").font(.title2.bold())
                        Spacer()
                        selectAll
                    }
                    VStack(spacing: 0) {
                        ForEach(profile.histories) { item in
                            Button {
                                if histories.contains(item.id) { histories.remove(item.id) } else { histories.insert(item.id) }
                            } label: {
                                HStack(alignment: .top, spacing: 16) {
                                    HistoryTimeline(histories: [item])
                                    Image(systemName: histories.contains(item.id) ? "checkmark.square.fill" : "square")
                                        .font(.title2).foregroundStyle(histories.contains(item.id) ? DearbyStyle.teal : DearbyStyle.quiet)
                                        .frame(width: 44, height: 44)
                                }.foregroundStyle(.primary)
                            }.buttonStyle(.plain).accessibilityValue(histories.contains(item.id) ? "공개" : "숨김")
                                .accessibilityAddTraits(histories.contains(item.id) ? .isSelected : [])
                        }
                    }
                    Text("게시하면 전체 프로필을 계정에 저장하고, 선택한 연락처와 이력만 명함으로 공개합니다.")
                        .font(.footnote).foregroundStyle(DearbyStyle.quiet)
                    if let error { Text(error).foregroundStyle(.red) }
                    Button(saving ? "게시 중…" : "공유 카드 만들기", action: submit)
                        .buttonStyle(DearbyButtonStyle()).disabled(saving || name.trimmingCharacters(in: .whitespaces).isEmpty)
                    Button("미리보기") { preview = true }.buttonStyle(DearbyButtonStyle(outlined: true))
                }.padding(20)
            }.background(.white).disabled(saving)
                .navigationTitle("직접 만들기").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() }.disabled(saving) }
                    ToolbarItem(placement: .primaryAction) { DearbyLogo(width: 66) }
                }
                .interactiveDismissDisabled(saving)
                .safeAreaInset(edge: .bottom) {
                    if let notice {
                        Text(notice).font(.footnote).foregroundStyle(.white).padding(14)
                            .background(.black.opacity(0.85), in: Capsule()).padding(12).accessibilityAddTraits(.updatesFrequently)
                    }
                }
                .task(id: notice) {
                    guard notice != nil else { return }
                    do { try await Task.sleep(for: .seconds(2)); notice = nil } catch {}
                }
                .sheet(isPresented: $preview) {
                    NavigationStack {
                        ScrollView { CardView(card: previewCard, onContact: { _ in }).disabled(true).padding(20) }
                            .navigationTitle("미리보기").navigationBarTitleDisplayMode(.inline)
                            .toolbar { Button("닫기") { preview = false } }
                    }
                }
        }
    }
    private var previewCard: CardModel {
        CardModel(id: "preview", ownerId: "", name: name, description: description, profileName: profile.name,
            job: profile.job, introduction: profile.introduction, contacts: profile.contacts.filter { contacts.contains($0.id) },
            histories: profile.histories.filter { histories.contains($0.id) }, createdAt: "")
    }
    private func submit() {
        saving = true
        Task {
            defer { saving = false }
            do {
                try await publish(CardRequest(name: name, description: description, contactIds: Array(contacts), historyIds: Array(histories)))
                dismiss()
            } catch { self.error = error.localizedDescription }
        }
    }
    private var selectAll: some View {
        let ids = profile.histories.map(\.id)
        let all = !ids.isEmpty && ids.allSatisfy { histories.contains($0) }
        let partial = !histories.isEmpty && !all
        return Button { histories = all ? [] : Set(ids) } label: {
            Label("전체 선택", systemImage: all ? "checkmark.square.fill" : (partial ? "minus.square.fill" : "square"))
                .font(.caption).frame(minHeight: 44)
        }.disabled(ids.isEmpty).accessibilityValue(all ? "전체 선택됨" : (partial ? "일부 선택됨" : "선택 안 됨"))
    }
}
