import SwiftUI

struct ProfilePage<Account: View>: View {
    let isAuthenticated: Bool
    let profile: ProfileModel
    let save: (ProfileModel) throws -> Void
    let account: Account
    @State private var editing = false
    var body: some View {
        Group {
            if isAuthenticated { profileContent } else {
                ContentUnavailableView {
                    Label("내 프로필", systemImage: "person.crop.circle")
                } description: {
                    Text("이메일로 로그인하고 나를 소개할 프로필을 작성하세요.")
                } actions: { account }
            }
        }.navigationTitle("내 프로필")
    }
    private var profileContent: some View {
        List {
            Section {
                Text(profile.name.isEmpty ? "내 이름을 입력해 주세요" : profile.name).font(.title2.bold())
                Text(profile.job.isEmpty ? "직무를 추가해 주세요" : profile.job).foregroundStyle(.secondary)
                Text(profile.introduction)
            }
            Section("연락처") {
                ForEach(profile.contacts) { contact in
                    LabeledContent(contact.label.isEmpty ? contact.kind : contact.label, value: contact.value)
                }
                Button("연락처 추가") { editing = true }
            }
            Section("활동 이력") {
                ForEach(profile.histories) { history in
                    VStack(alignment: .leading) { Text(history.title); Text(history.role).foregroundStyle(.secondary) }
                }
                Button("활동 이력 추가") { editing = true }
            }
            Section("계정") { account }
        }
        .navigationTitle("내 프로필")
        .toolbar { Button("편집") { editing = true } }
        .sheet(isPresented: $editing) { ProfileEditor(draft: profile, save: save) }
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
