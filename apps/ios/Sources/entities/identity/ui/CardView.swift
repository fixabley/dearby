import SwiftUI

/// One published card as a single surface (contract "명함 표현", 2026-10-18): centered header, introduction,
/// 연락처 rows that open the contact, and 활동 이력 rows. Action buttons belong to the screen, below the card.
struct CardView: View {
    let card: CardModel
    let onContact: (ContactModel) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            header
            if !card.introduction.isEmpty { DearbyMarkdownText(source: card.introduction) }
            section("연락처") {
                if card.contacts.isEmpty {
                    empty("공개된 연락처가 없어요")
                } else {
                    ForEach(card.contacts) { contact in
                        ContactRow(contact: contact, open: onContact)
                        if contact.id != card.contacts.last?.id { Divider() }
                    }
                }
            }
            section("활동 이력") {
                if card.histories.isEmpty {
                    empty("공개된 활동 이력이 없어요")
                } else {
                    ForEach(card.histories) { history in
                        HistoryRow(history: history)
                        if history.id != card.histories.last?.id { Divider() }
                    }
                }
            }
        }
        .padding(20).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
    }
    private var header: some View {
        VStack(spacing: 10) {
            DearbyPhotoAvatar(photoUrl: card.photoUrl, name: card.profileName, size: 72)
            Text(card.profileName).font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.ink).multilineTextAlignment(.center)
            if !card.jobs.isEmpty {
                ChipFlow(spacing: 6, centered: true) {
                    ForEach(card.jobs, id: \.self) { job in
                        Text(job).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.ink)
                            .padding(.horizontal, 10).padding(.vertical, 5).background(DearbyStyle.muted, in: Capsule())
                    }
                }.accessibilityElement(children: .ignore).accessibilityLabel("직무 " + card.job)
            }
            if let organization = card.organization, !organization.isEmpty {
                Text(organization).font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
            }
        }.frame(maxWidth: .infinity)
    }
    private func section(_ title: String, @ViewBuilder rows: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.quiet).accessibilityAddTraits(.isHeader)
            rows()
        }
    }
    private func empty(_ text: String) -> some View {
        Text(text).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).padding(.vertical, 6)
    }
}

/// Contact row (contract "연락처 행은 라벨 대신 왼쪽 아이콘" 2026-10-20): kind glyph on the left, the value, and an external-link mark when the row
/// can open something. The kind (or the owner's label) is VoiceOver-only.
private struct ContactRow: View {
    let contact: ContactModel
    let open: (ContactModel) -> Void
    var body: some View {
        if let url = contact.actionURL {
            Button { open(contact) } label: { content(openable: true) }.buttonStyle(.plain)
                .accessibilityLabel(contact.rowLabel + ": " + contact.value)
                .accessibilityHint(url.scheme == "https" ? "링크를 열어요" : contact.kind == "phone" ? "전화를 걸어요" : "메일을 보내요")
        } else {
            content(openable: false).accessibilityElement(children: .ignore).accessibilityLabel(contact.rowLabel + ": " + contact.value)
        }
    }
    private func content(openable: Bool) -> some View {
        HStack(spacing: 12) {
            ContactSymbol(kind: contact.kind)
            Text(contact.value).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink)
            Spacer(minLength: 0)
            if openable { Image(systemName: "arrow.up.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(DearbyStyle.quiet) }
        }.padding(.vertical, 8).frame(minHeight: 44).contentShape(Rectangle())
    }
}

/// Airbnb-style credential row: period, 조직 · 프로그램 · 활동 title, role with the verification badge, optional description.
private struct HistoryRow: View {
    let history: HistoryModel
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(history.startDate + (history.endDate.map { " – " + $0 } ?? "")).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
            Text(history.title).font(.dearby(.subheadline).weight(.bold)).foregroundStyle(DearbyStyle.ink)
            HStack(spacing: 8) {
                if !history.role.isEmpty { Text(history.role).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet) }
                HistoryBadge(verified: history.verified)
            }
            if !history.description.isEmpty { Text(history.description).font(.dearby(.footnote)).foregroundStyle(DearbyStyle.ink) }
        }.padding(.vertical, 8).frame(maxWidth: .infinity, alignment: .leading)
    }
}
