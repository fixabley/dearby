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
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(profile.name).font(.title2.bold())
                    Text(profile.job).foregroundStyle(.secondary)
                    Text(profile.introduction)
                    Text("이름 · 직무 · 소개는 공개됩니다.").font(.footnote)
                }
                Section("명함") {
                    TextField("명함 이름", text: $name)
                    TextField("설명", text: $description)
                }
                Section("공개할 연락처") {
                    selectAll(ids: profile.contacts.map(\.id), selection: $contacts)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 130))]) {
                        ForEach(profile.contacts) { item in
                            Button {
                                if contacts.contains(item.id) { contacts.remove(item.id); notice = "\(item.displayLabel)을 숨겼습니다." } else { contacts.insert(item.id); notice = "\(item.displayLabel)을 공개합니다." }
                            } label: {
                                Label(item.displayLabel, systemImage: contacts.contains(item.id) ? item.symbol : "eye.slash")
                                    .frame(minHeight: 44)
                                    .foregroundStyle(contacts.contains(item.id) ? .teal : .secondary)
                            }.buttonStyle(.bordered)
                                .accessibilityValue(contacts.contains(item.id) ? "공개" : "숨김")
                        }
                    }
                }
                Section("공개할 활동 이력") {
                    selectAll(ids: profile.histories.map(\.id), selection: $histories)
                    ForEach(profile.histories) { item in
                        Button {
                            if histories.contains(item.id) { histories.remove(item.id) } else { histories.insert(item.id) }
                        } label: {
                            HStack {
                                Image(systemName: histories.contains(item.id) ? "checkmark.square.fill" : "square")
                                VStack(alignment: .leading) {
                                    Text(item.title); Text(item.role).font(.caption).foregroundStyle(.secondary)
                                }
                            }.frame(minHeight: 44)
                        }.accessibilityValue(histories.contains(item.id) ? "공개" : "숨김")
                    }
                }
                Text("게시하면 전체 프로필을 계정에 저장하고, 선택한 연락처와 이력만 명함으로 공개합니다.")
                    .font(.footnote).foregroundStyle(.secondary)
                if let error { Text(error).foregroundStyle(.red) }
                Button(saving ? "게시 중…" : "이 명함 게시하기") {
                    saving = true
                    Task {
                        defer { saving = false }
                        do {
                            try await publish(CardRequest(name: name, description: description,
                                contactIds: Array(contacts), historyIds: Array(histories)))
                            dismiss()
                        } catch { self.error = error.localizedDescription }
                    }
                }.disabled(saving || name.trimmingCharacters(in: .whitespaces).isEmpty)
            }.disabled(saving)
                .navigationTitle("새 명함")
                .toolbar { Button("취소") { dismiss() }.disabled(saving) }
                .interactiveDismissDisabled(saving)
                .safeAreaInset(edge: .bottom) {
                    if let notice { Text(notice).font(.footnote).padding().background(.regularMaterial).accessibilityAddTraits(.updatesFrequently) }
                }
                .task(id: notice) {
                    guard notice != nil else { return }
                    do { try await Task.sleep(for: .seconds(2)); notice = nil } catch {}
                }
        }
    }
    private func selectAll(ids: [String], selection: Binding<Set<String>>) -> some View {
        let all = !ids.isEmpty && ids.allSatisfy { selection.wrappedValue.contains($0) }
        let partial = !selection.wrappedValue.isEmpty && !all
        return Button {
            selection.wrappedValue = all ? [] : Set(ids)
        } label: {
            Label("전체 선택", systemImage: all ? "checkmark.square.fill" : (partial ? "minus.square.fill" : "square"))
                .frame(minHeight: 44)
        }.disabled(ids.isEmpty).accessibilityValue(all ? "전체 선택됨" : (partial ? "일부 선택됨" : "선택 안 됨"))
    }
}
