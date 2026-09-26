import SwiftUI

struct CardView: View {
    let card: CardModel
    let onContact: (ContactModel) -> Void
    @State private var expanded = false
    @State private var notice: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 6) {
                Text(card.profileName).font(.largeTitle.bold())
                Text(card.job).foregroundStyle(.secondary)
            }
            Divider()
            Text(card.name).font(.caption.bold()).padding(8).background(.teal.opacity(0.10), in: Capsule())
            Text(card.description).font(.title3.bold())
            Text(card.introduction)
            if expanded {
                Text("연락처").font(.headline)
                FlowContacts(contacts: card.contacts) { contact in
                    onContact(contact)
                    if case .copy = contact.action { notice = "카카오톡 ID를 복사했습니다." }
                }
                if let notice { Text(notice).font(.caption).foregroundStyle(.secondary) }
                if !card.histories.isEmpty { Text("활동 이력").font(.headline) }
                ForEach(card.histories) { item in
                    HStack(alignment: .top, spacing: 12) {
                        Circle().fill(.teal).frame(width: 8, height: 8).padding(.top, 6)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.startDate + " · " + (item.endDate ?? "진행 중")).font(.caption).foregroundStyle(.secondary)
                            Text(item.title).font(.headline)
                            Text(item.role)
                            Text(item.description).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            Button(expanded ? "접기" : "상세보기", systemImage: expanded ? "chevron.up" : "chevron.down") {
                expanded.toggle()
            }.frame(minHeight: 44)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(24).background(.background, in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(.teal.opacity(0.2)))
    }
}
private struct FlowContacts: View {
    let contacts: [ContactModel]
    let action: (ContactModel) -> Void
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), alignment: .leading)], alignment: .leading, spacing: 12) {
            ForEach(contacts) { contact in
                Button { action(contact) } label: {
                    Label(contact.displayLabel, systemImage: contact.symbol).frame(minHeight: 44)
                }
                .accessibilityLabel(contact.displayLabel + ": " + contact.value)
                .disabled({ if case .unavailable = contact.action { true } else { false } }())
            }
        }
    }
}
