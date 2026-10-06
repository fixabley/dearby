import Observation

/// Example profile for the 내 프로필 tab until it reads the account's real profile (#149).
@MainActor @Observable final class IdentityViewModel {
    var signedIn = false
    var profileName = "김지민"
    var introduction = "사람을 연결하는 경험을 만듭니다."
    var job = "서비스 기획 · 커뮤니티"
    var contacts = DemoIdentity.contacts
    var histories = DemoIdentity.histories
}
