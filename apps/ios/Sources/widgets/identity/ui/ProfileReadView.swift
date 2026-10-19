import SwiftUI

/// 프로필 읽기 모드 본문(contract "프로필 읽기/수정 모드" 2026-10-20): 명함 본문과 같은 표현 — 머리(이름·직무·소개), 연락처는 왼쪽 아이콘 + 값,
/// 활동 이력은 `HistoryTimeline`. 입력 칸은 없다. 프로필 탭 읽기 모드와 변경 이력의 버전 상세가 함께 쓴다.
struct ProfileReadView: View {
    let profile: AccountProfile
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            IdentityHeading(name: profile.name, job: profile.job, introduction: profile.introduction, isFullProfile: true)
            Divider()
            Text("연락처").font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
            ForEach(profile.contacts) { contact in
                HStack(spacing: 12) { ContactSymbol(kind: contact.kind); Text(contact.value).font(.dearby(.subheadline)) }
                    .padding(.vertical, 4).accessibilityElement(children: .ignore).accessibilityLabel(contact.label + ": " + contact.value)
            }
            Divider()
            Text("활동 이력").font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
            if profile.histories.isEmpty {
                Text("아직 이력이 없어요.").font(.dearby(.subheadline)).foregroundStyle(.secondary)
            } else {
                HistoryTimeline(histories: profile.histories.map(\.historyModel), compact: true)
            }
        }
    }
}
