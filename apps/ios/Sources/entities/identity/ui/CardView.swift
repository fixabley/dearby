import SwiftUI

struct CardView: View {
    let card: CardModel
    let onContact: (ContactModel) -> Void
    @State private var notice: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            IdentityHeading(name: card.profileName, job: card.job, introduction: card.introduction)
            if !card.description.isEmpty { Text(card.description).foregroundStyle(.secondary) }
            ContactIcons(contacts: card.contacts) { contact in
                onContact(contact)
                if case .copy = contact.action { notice = "카카오톡 ID를 복사했습니다." }
            }
            if let notice { Text(notice).font(.caption).foregroundStyle(.secondary) }
            Divider()
            HStack {
                Text("활동 이력").font(.title2.bold())
                Spacer()
                Text("직접 작성").font(.caption).foregroundStyle(.secondary)
            }
            if card.histories.isEmpty { Text("공개한 활동 이력이 없어요.").font(.subheadline).foregroundStyle(.secondary) }
            HistoryTimeline(histories: card.histories)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct CardDeck: View {
    let cards: [CardModel]
    @Binding var selection: Int
    var body: some View {
        VStack(spacing: 12) {
            if !cards.isEmpty {
                ZStack(alignment: .top) {
                    ForEach(Array(upcoming.reversed()), id: \.offset) { item in
                        header(item.element).padding(16)
                            .background(item.offset == 1 ? DearbyStyle.teal.opacity(0.1) : DearbyStyle.muted,
                                in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
                            .padding(.horizontal, CGFloat(item.offset) * 10)
                            .offset(y: CGFloat(upcoming.count - item.offset) * 34)
                    }
                    summary(cards[min(selection, cards.count - 1)])
                        .padding(20).background(DearbyStyle.mint, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.teal.opacity(0.25)))
                        .padding(.top, CGFloat(upcoming.count) * 34)
                }.contentShape(Rectangle()).gesture(DragGesture(minimumDistance: 30).onEnded { value in
                    guard abs(value.translation.height) > abs(value.translation.width) else { return }
                    move(value.translation.height < 0 ? 1 : -1)
                })
                HStack(spacing: 22) {
                    Button { move(-1) } label: { Image(systemName: "chevron.up").frame(width: 44, height: 44) }
                        .accessibilityLabel("이전 명함").disabled(selection == 0)
                    Text("\(min(selection + 1, cards.count)) / \(cards.count)").font(.caption).foregroundStyle(.secondary)
                    Button { move(1) } label: { Image(systemName: "chevron.down").frame(width: 44, height: 44) }
                        .accessibilityLabel("다음 명함").disabled(selection >= cards.count - 1)
                }.foregroundStyle(DearbyStyle.teal)
            }
        }.onChange(of: cards.map(\.id)) { _, _ in selection = min(selection, max(0, cards.count - 1)) }
    }
    private var upcoming: [(offset: Int, element: CardModel)] {
        guard cards.count > 1 else { return [] }
        return (1...min(2, cards.count - 1)).map { ($0, cards[(min(selection, cards.count - 1) + $0) % cards.count]) }
    }
    private func move(_ delta: Int) { selection = min(max(0, selection + delta), cards.count - 1) }
    private func header(_ card: CardModel) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 14) {
            Text(card.profileName).font(.title3.bold()).foregroundStyle(.primary)
            Text(card.job).font(.subheadline).foregroundStyle(.secondary)
            Spacer(minLength: 0)
        }
    }
    private func summary(_ card: CardModel) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            header(card)
            Divider()
            Text(card.name).font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            Text(card.description).font(.subheadline).foregroundStyle(.secondary)
            Text("연락처").font(.subheadline.bold())
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), alignment: .leading)], alignment: .leading, spacing: 10) {
                ForEach(card.contacts) { contact in
                    Label(contact.displayLabel, systemImage: contact.symbol).font(.subheadline)
                        .foregroundStyle(.secondary).frame(minHeight: 32)
                }
            }
            Divider()
            Text("활동 이력").font(.subheadline.bold())
            if card.histories.isEmpty { Text("공개한 활동 이력이 없어요.").font(.caption).foregroundStyle(.secondary) }
            HistoryTimeline(histories: Array(card.histories.prefix(3)))
            if card.histories.count > 3 { Text("외 \(card.histories.count - 3)개 · 상세보기에서 확인").font(.caption) }
        }
    }
}
