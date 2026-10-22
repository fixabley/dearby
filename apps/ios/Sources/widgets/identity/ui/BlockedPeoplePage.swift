import SwiftUI

/// 설정 › 차단한 사람 (contract "명함 신고·차단"): `GET /v1/wallet/blocks`, unblocked by swipe or the edit mode's delete.
/// Unblocking does not bring removed cards back.
struct BlockedPeoplePage: View {
    let account: AccountViewModel
    @State private var people: [BlockedPerson]?
    @State private var failed = false
    @State private var notice: String?
    var body: some View {
        List {
            if let people {
                if people.isEmpty {
                    ContentUnavailableView("차단한 사람이 없어요", systemImage: "hand.raised", description: Text("받은 명함의 더 보기 메뉴에서 차단할 수 있어요."))
                        .listRowBackground(Color.clear)
                } else {
                    Section {
                        ForEach(people) { person in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(person.name)
                                Text("차단한 날 " + SettingsPage.day(person.blockedAt)).font(.dearby(.footnote)).foregroundStyle(.secondary)
                            }
                            .swipeActions { Button("차단 해제", role: .destructive) { Task { await unblock(person) } } }
                        }
                        .onDelete { offsets in Task { for index in offsets { await unblock(people[index]) } } }
                    } footer: { Text("차단을 해제해도 지운 명함은 돌아오지 않아요. 다시 받으려면 QR을 읽어 주세요.").font(.dearby(.footnote)) }
                }
            } else if failed {
                Section {
                    Label("목록을 불러오지 못했어요", systemImage: "exclamationmark.triangle").foregroundStyle(.secondary)
                    Button("다시 시도") { Task { await load() } }
                }
            } else {
                Section { ProgressView().frame(maxWidth: .infinity, minHeight: 88) }
            }
        }
        .listStyle(.insetGrouped).font(.dearby(.body))
        .navigationTitle("차단한 사람").navigationBarTitleDisplayMode(.inline)
        .toolbar { if people?.isEmpty == false { EditButton() } }
        .task { await load() }
        .refreshable { await load() }
        .alert(notice ?? "", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) { Button("확인", role: .cancel) {} }
    }
    private func load() async {
        failed = false
        do { people = try await account.authorized { [client = account.client] in try await client.blocks($0) } } catch { if people == nil { failed = true } }
    }
    private func unblock(_ person: BlockedPerson) async {
        do {
            try await account.authorized { [client = account.client] in try await client.unblock(person.id, $0) }
            people?.removeAll { $0.id == person.id }
        } catch {
            notice = "차단을 해제하지 못했어요. 연결을 확인하고 다시 시도해 주세요."
        }
    }
}
