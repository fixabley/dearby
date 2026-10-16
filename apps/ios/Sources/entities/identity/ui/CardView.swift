import SwiftUI

struct CardView: View {
    let card: CardModel
    let onContact: (ContactModel) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            IdentityHeading(name: card.profileName, job: card.job, introduction: "")
            // The body is the card's filled-in markdown template.
            if !card.introduction.isEmpty { DearbyMarkdownText(source: card.introduction) }
            ContactIcons(contacts: card.contacts, action: onContact)
            Divider()
            HStack {
                Text("활동 이력").font(.title2.bold())
                Spacer()
                Text("본인 입력 · 미확인은 인증자료 미확인").font(.caption).foregroundStyle(DearbyStyle.quiet)
            }
            if card.histories.isEmpty { Text("공개한 활동 이력이 없어요.").font(.subheadline).foregroundStyle(DearbyStyle.quiet) }
            HistoryTimeline(histories: card.histories)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
