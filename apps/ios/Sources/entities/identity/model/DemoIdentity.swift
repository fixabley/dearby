import Foundation

struct ContactModel: Identifiable, Equatable {
    let id: String
    let kind: String
    var displayLabel: String
    var value: String
    var symbol: String {
        switch kind {
        case "phone": "phone"
        case "email": "envelope"
        case "chat": "bubble"
        case "instagram": "camera"
        default: "link"
        }
    }
}
struct HistoryModel: Identifiable, Equatable {
    let id: String
    var title: String
    var role: String
    var startDate: String
    var endDate: String?
}
struct CardModel: Identifiable, Equatable {
    let id: String
    var name: String
    var profileName: String
    var job: String
    var introduction: String
    var description: String
    var contacts: [ContactModel]
    var histories: [HistoryModel]
}
enum DemoIdentity {
    static let contacts: [ContactModel] = [
        .init(id: "phone", kind: "phone", displayLabel: "전화번호", value: "010-0000-0000"),
        .init(id: "email", kind: "email", displayLabel: "이메일", value: "jimin@example.com"),
        .init(id: "chat", kind: "chat", displayLabel: "카카오톡", value: "https://example.com"),
        .init(id: "instagram", kind: "instagram", displayLabel: "인스타그램", value: "@jimin_example"),
        .init(id: "github", kind: "github", displayLabel: "GitHub", value: "jimin-example"),
        .init(id: "behance", kind: "behance", displayLabel: "Behance", value: "jimin-example")
    ]
    static let histories: [HistoryModel] = [
        .init(id: "conference", title: "Dearby 개발자 컨퍼런스", role: "운영 스태프", startDate: "2026.09"),
        .init(id: "camp", title: "Dearby 메이커 캠프", role: "서비스 기획", startDate: "2026.03", endDate: "08"),
        .init(id: "hackathon", title: "캠퍼스 해커톤", role: "서비스 기획", startDate: "2025.11")
    ]
    static let myCards = ["네트워킹", "프로젝트 소개", "커뮤니티"].enumerated().map { index, title in
        CardModel(id: "mine-\(index)", name: title, profileName: "김지민", job: "서비스 기획",
                  introduction: "사람을 연결하는 경험을 만듭니다.", description: "새로운 인연에게 나를 소개해요.",
                  contacts: contacts.filter { ["email", "chat", "github", "behance"].contains($0.id) }, histories: histories)
    }
    static let received = ["최유진", "박서연", "이도윤", "정하린", "김현우"].enumerated().map { index, name in
        CardModel(id: "received-\(index)", name: index == 0 ? "디자인과 협업" : "함께하는 커뮤니티",
                  profileName: name, job: index == 0 ? "프로덕트 디자이너" : "커뮤니티 운영",
                  introduction: "함께 성장하는 경험을 만들어요.", description: "일상 속 불편을 함께 풀어요.",
                  contacts: contacts.filter { ["email", "chat"].contains($0.id) }, histories: histories)
    }
}
