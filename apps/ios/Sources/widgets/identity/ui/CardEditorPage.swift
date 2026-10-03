import SwiftUI

struct CardEditorPage: View {
    let state: IdentityViewModel
    let editing: CardModel?
    @State private var name: String
    @State private var contactIDs: Set<String>
    @State private var historyIDs: Set<String>
    @State private var preset = false
    @State private var preview = false
    @State private var notice = ""
    @Environment(\.dismiss) private var dismiss
    init(state: IdentityViewModel, editing: CardModel? = nil) {
        self.state = state
        self.editing = editing
        _name = State(initialValue: editing?.name ?? "네트워킹")
        _contactIDs = State(initialValue: editing.map { Set($0.contacts.map(\.id)) } ?? state.presetContactIDs ?? ["email", "chat", "github", "behance"])
        _historyIDs = State(initialValue: editing.map { Set($0.histories.map(\.id)) } ?? state.presetHistoryIDs ?? ["conference", "camp"])
    }
    private var card: CardModel {
        CardModel(id: "preview", name: name, profileName: state.profileName, job: state.job,
            introduction: state.introduction, description: "새로운 인연에게 나를 소개해요.",
            contacts: state.contacts.filter { contactIDs.contains($0.id) }, histories: state.histories.filter { historyIDs.contains($0.id) })
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                IdentityHeading(name: state.profileName, job: "", introduction: state.introduction)
                TextField("명함 이름", text: $name).textFieldStyle(.roundedBorder).accessibilityIdentifier("card-name")
                VStack(alignment: .leading, spacing: 8) {
                    Text("공개할 연락처").font(.title2.bold())
                    Text("눌러서 공유할 연락처를 골라 주세요.").font(.subheadline).foregroundStyle(DearbyStyle.quiet)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 60))], spacing: 12) {
                        ForEach(state.contacts.filter { $0.kind != "instagram" }) { contact in
                            Button {
                                if !contactIDs.insert(contact.id).inserted { contactIDs.remove(contact.id) }
                                notice = "\(contact.displayLabel)을 이번 공유에서 \(contactIDs.contains(contact.id) ? "공개해요" : "숨겼어요")"
                            } label: {
                                VStack(spacing: 8) {
                                    ContactSymbol(contact: contact).overlay {
                                        if !contactIDs.contains(contact.id) {
                                            Rectangle().fill(DearbyStyle.quiet).frame(width: 36, height: 2).rotationEffect(.degrees(-45))
                                        }
                                    }
                                    Text(contact.displayLabel).font(.caption)
                                }.frame(maxWidth: .infinity, minHeight: 65)
                                    .foregroundStyle(contactIDs.contains(contact.id) ? DearbyStyle.teal : DearbyStyle.quiet)
                                    .opacity(contactIDs.contains(contact.id) ? 1 : 0.5)
                            }.accessibilityIdentifier("contact-\(contact.id)")
                                .accessibilityAddTraits(contactIDs.contains(contact.id) ? .isSelected : [])
                        }
                    }
                }
                Divider()
                HStack {
                    Text("활동 이력").font(.title2.bold())
                    Spacer()
                    Button("전체 선택", systemImage: historyIDs.count == state.histories.count ? "checkmark.square.fill" : "square") {
                        historyIDs = historyIDs.count == state.histories.count ? [] : Set(state.histories.map(\.id))
                    }.font(.caption)
                }
                ForEach(state.histories) { history in
                    Button {
                        if !historyIDs.insert(history.id).inserted { historyIDs.remove(history.id) }
                    } label: {
                        HStack {
                            HistoryTimeline(histories: [history])
                            Image(systemName: historyIDs.contains(history.id) ? "checkmark.square.fill" : "square").font(.title2)
                        }.foregroundStyle(.primary)
                    }.buttonStyle(.plain).accessibilityIdentifier("history-\(history.id)")
                }
                Toggle("이 구성을 프리셋으로 저장", isOn: $preset).font(.subheadline)
                if !notice.isEmpty { Text(notice).font(.caption).foregroundStyle(DearbyStyle.teal) }
            }.padding(20)
        }.safeAreaInset(edge: .bottom) {
            VStack(spacing: 8) {
                Button(editing == nil ? "공유 카드 만들기" : "수정 완료") {
                    if preset { state.presetContactIDs = contactIDs; state.presetHistoryIDs = historyIDs }
                    _ = state.makeCard(name: name.isEmpty ? "새 명함" : name, editingID: editing?.id, contactIDs: contactIDs, historyIDs: historyIDs)
                    dismiss()
                }.buttonStyle(DearbyButtonStyle())
                Button("미리보기") { preview = true }.buttonStyle(DearbyButtonStyle(outlined: true))
            }.padding(20).background(.white)
        }.navigationTitle("직접 만들기").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
            .sheet(isPresented: $preview) {
                NavigationStack {
                    ScrollView { CardView(card: card, onContact: { _ in }).padding(20) }
                        .navigationTitle("미리보기").toolbar { Button("닫기") { preview = false } }
                }
            }
    }
}
