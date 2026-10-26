import SwiftUI

/// 프로필 읽기 모드 본문 (contract "프로필 읽기/수정 모드" 2026-10-20, "프로필 보기 모드 = 명함 디자인" 2026-10-26): 맨 위는 명함 면(`CardView`
/// faceOnly — 사진·이름·직무·연락처), 그 아래 소개와 활동 이력(`HistoryTimeline`). 입력 칸은 없다. 프로필 탭 읽기 모드와 변경 이력의 버전 상세가 함께
/// 쓴다(버전에는 사진이 없어 머리글자).
struct ProfileReadView: View {
    let profile: AccountProfile
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            CardView(card: profile.cardModel, faceOnly: true)
            if !profile.introduction.isEmpty {
                Text("소개").font(.dearby(.headline)).accessibilityAddTraits(.isHeader).padding(.top, 8)
                DearbyMarkdownText(source: profile.introduction)
            }
            Text("활동 이력").font(.dearby(.headline)).accessibilityAddTraits(.isHeader).padding(.top, 8)
            if profile.histories.isEmpty {
                Text("아직 이력이 없어요.").font(.dearby(.subheadline)).foregroundStyle(.secondary)
            } else {
                HistoryTimeline(histories: profile.histories.map(\.historyModel), compact: true)
            }
        }
    }
}

extension AccountProfile {
    /// The profile as a card face: every contact and history, as 이 내용으로 명함 만들기 starts with them.
    var cardModel: CardModel {
        CardModel(id: "profile", name: "", profileName: name, job: job, introduction: introduction, description: "",
                  contacts: contacts.map { ContactModel(id: $0.id, kind: $0.kind, displayLabel: $0.label, value: $0.value) },
                  histories: histories.map(\.historyModel), organization: histories.latestOrganization, photoUrl: photoUrl)
    }
}
