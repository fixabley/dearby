import SwiftUI

/// One published card as a single surface (contract "명함 표현", 2026-10-18): centered header, introduction,
/// 연락처 rows that open the contact, and 활동 이력 rows. Action buttons belong to the screen, below the card.
/// `faceOnly` keeps the face (photo, name, 직무, 연락처) for 내 프로필 (contract "프로필 보기 모드 = 명함 디자인" 2026-10-26), which shows 소개·이력 below it.
struct CardView: View {
    let card: CardModel
    var faceOnly = false
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            header
            if !faceOnly && !card.introduction.isEmpty { DearbyMarkdownText(source: card.introduction) }
            section("연락처") {
                if card.contacts.isEmpty {
                    empty("공개된 연락처가 없어요")
                } else {
                    ForEach(card.contacts) { contact in
                        ContactRow(contact: contact)
                        if contact.id != card.contacts.last?.id { Divider() }
                    }
                }
            }
            if !faceOnly {
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

/// Contact row (contract "연락처 행은 라벨 대신 왼쪽 아이콘" 2026-10-20, "연락처 아이콘·바로가기" 2026-10-26): kind glyph on the left, the value, and
/// a mark for what a tap does — open (`arrow.up.right`) or copy a KakaoTalk ID (`doc.on.doc`, then "ID를 복사했어요" for a moment).
/// The kind (or the owner's label) is VoiceOver-only.
private struct ContactRow: View {
    let contact: ContactModel
    @Environment(\.openURL) private var openURL
    @State private var copied = false
    var body: some View {
        switch contact.action {
        case .open(let url):
            Button { openURL(url) } label: { content(mark: "arrow.up.right") }.buttonStyle(.plain)
                .accessibilityLabel(contact.rowLabel + ": " + contact.displayValue)
                .accessibilityHint(contact.kind == "phone" ? "전화를 걸어요" : contact.kind == "email" ? "메일을 보내요" : "링크를 열어요")
        case .copy(let id):
            Button { copy(id) } label: { content(mark: "doc.on.doc") }.buttonStyle(.plain)
                .accessibilityLabel(contact.rowLabel + ": " + contact.displayValue).accessibilityHint("ID를 복사해요")
        case nil:
            content(mark: nil).accessibilityElement(children: .ignore).accessibilityLabel(contact.rowLabel + ": " + contact.displayValue)
        }
    }
    private func content(mark: String?) -> some View {
        HStack(spacing: 12) {
            DearbyGlyphView(glyph: ContactModel.glyph(kind: contact.kind))
            Text(contact.displayValue).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink)
            Spacer(minLength: 0)
            if copied {
                Text("ID를 복사했어요").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
            } else if let mark {
                Image(systemName: mark).font(.dearby(.footnote).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
            }
        }.padding(.vertical, 8).frame(minHeight: 44).contentShape(Rectangle())
    }
    private func copy(_ id: String) {
        UIPasteboard.general.string = id
        AccessibilityNotification.Announcement("ID를 복사했어요").post()
        withAnimation { copied = true }
        Task {
            try? await Task.sleep(for: .seconds(2))
            withAnimation { copied = false }
        }
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
