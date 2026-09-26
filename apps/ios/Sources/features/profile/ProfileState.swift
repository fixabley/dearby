import Foundation
import Observation

@MainActor @Observable final class ProfileState {
    private let store: LocalStore
    private var key = "profile.guest"
    var value = ProfileModel()
    init(store: LocalStore) throws {
        self.store = store
        value = try store.read(ProfileModel.self, key: key) ?? ProfileModel()
    }
    func selectAccount(_ accountID: String?) throws {
        let newKey = accountID.map { "profile.\($0)" } ?? "profile.guest"
        let restored = try store.read(ProfileModel.self, key: newKey) ?? ProfileModel()
        key = newKey
        value = restored
    }
    func save(_ draft: ProfileModel) throws {
        try store.write(draft, key: key)
        value = draft
    }
    // Server refresh is explicit; it must not silently replace an offline local edit.
    func restoreServerIfMissing(_ server: ProfileModel) throws {
        if try store.read(ProfileModel.self, key: key) == nil { try save(server) }
    }
}
struct ProfileRequest: Encodable {
    let name: String
    let job: String
    let introduction: String
    let contacts: [ContactModel]
    let histories: [HistoryModel]
    init(_ profile: ProfileModel) {
        name = profile.name; job = profile.job; introduction = profile.introduction
        contacts = profile.contacts; histories = profile.histories
    }
}
