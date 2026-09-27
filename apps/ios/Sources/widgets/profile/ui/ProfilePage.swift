import SwiftUI

struct ProfilePage<Account: View>: View {
    let isAuthenticated: Bool
    let profile: ProfileModel
    let save: (ProfileModel) throws -> Void
    let account: Account
    @State private var editing = false
    var body: some View {
        ScrollView {
            if isAuthenticated { profileContent } else {
                VStack(spacing: 24) {
                    Image(systemName: "person.text.rectangle").font(.system(size: 76, weight: .light))
                        .foregroundStyle(DearbyStyle.teal).padding(.top, 110).padding(.bottom, 16)
                    Text("나를 소개하는 명함을 만들어보세요.").font(.title2.bold()).multilineTextAlignment(.center)
                    Text("연락처와 활동 이력을 정리하고\n상황에 맞는 명함으로 공유할 수 있어요.")
                        .foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
                    account.buttonStyle(DearbyButtonStyle()).padding(.top, 24)
                    Text("명함 받기와 기기 저장은 로그인 없이 이용할 수 있어요.")
                        .font(.caption).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
                }.padding(20)
            }
        }.background(.white).navigationTitle("내 프로필").navigationBarTitleDisplayMode(.inline)
            .toolbar { if isAuthenticated { Button("편집") { editing = true } } }
            .sheet(isPresented: $editing) { ProfileEditor(draft: profile, save: save) }
    }
    private var profileContent: some View {
        VStack(alignment: .leading, spacing: 24) {
            IdentityHeading(name: profile.name, job: profile.job, introduction: profile.introduction, isFullProfile: true)
            Label("저장한 정보는 명함에서 선택한 항목만 공개돼요.", systemImage: "info.circle")
                .font(.caption).foregroundStyle(DearbyStyle.teal).padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(DearbyStyle.mint, in: RoundedRectangle(cornerRadius: 9))
            HStack {
                Text("연락처").font(.title3.bold())
                Spacer()
                Button("추가") { editing = true }.accessibilityLabel("연락처 추가")
            }
            VStack(spacing: 4) {
                ForEach(profile.contacts) { contact in
                    Button { editing = true } label: {
                        HStack(spacing: 12) {
                            ContactSymbol(contact: contact, size: 22).foregroundStyle(DearbyStyle.teal).frame(width: 24)
                            Text(contact.displayLabel).font(.subheadline)
                            Spacer(minLength: 8)
                            Text(contact.value).font(.subheadline).foregroundStyle(DearbyStyle.quiet)
                            Image(systemName: "chevron.right").font(.caption).foregroundStyle(DearbyStyle.quiet)
                        }.padding(12).frame(minHeight: 44).background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 8))
                    }.buttonStyle(.plain)
                }
            }
            HStack {
                Text("활동 이력").font(.title3.bold())
                Spacer()
                Button("추가") { editing = true }.accessibilityLabel("활동 이력 추가")
            }
            HistoryTimeline(histories: profile.histories, compact: true)
            Divider()
            account.frame(minHeight: 44)
        }.padding(20)
    }
}

struct ProfileEditor: View {
    @Environment(\.dismiss) private var dismiss
    @State var draft: ProfileModel
    let save: (ProfileModel) throws -> Void
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                Section("기본 정보") {
                    TextField("이름", text: $draft.name)
                    TextField("직무", text: $draft.job)
                    TextField("소개", text: $draft.introduction, axis: .vertical)
                }
                Section("연락처") {
                    ForEach($draft.contacts) { $contact in
                        VStack {
                            Picker("종류", selection: $contact.kind) {
                                ForEach(ContactModel.kinds, id: \.self) { Text($0).tag($0) }
                            }
                            TextField("표시 이름", text: $contact.label)
                            TextField("연락처", text: $contact.value).textInputAutocapitalization(.never)
                        }
                    }.onDelete { draft.contacts.remove(atOffsets: $0) }
                    Button("연락처 추가", systemImage: "plus") { draft.contacts.append(ContactModel()) }
                }
                Section("활동 이력") {
                    ForEach($draft.histories) { $history in
                        VStack {
                            TextField("활동 이름", text: $history.title)
                            TextField("역할", text: $history.role)
                            TextField("시작일 YYYY-MM-DD", text: $history.startDate)
                            TextField("종료일 YYYY-MM-DD (선택)", text: Binding(
                                get: { history.endDate ?? "" }, set: { history.endDate = $0.isEmpty ? nil : $0 }))
                            TextField("활동 설명", text: $history.description, axis: .vertical)
                        }
                    }.onDelete { draft.histories.remove(atOffsets: $0) }
                    Button("활동 이력 추가", systemImage: "plus") { draft.histories.append(HistoryModel()) }
                }
                if let error { Text(error).foregroundStyle(.red) }
                Text("기기에 저장됩니다. 기존에 발행한 명함은 바뀌지 않습니다.").font(.footnote)
            }
            .navigationTitle("프로필 편집")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") {
                        do { try save(draft); dismiss() } catch { self.error = error.localizedDescription }
                    }
                }
            }
        }
    }
}
