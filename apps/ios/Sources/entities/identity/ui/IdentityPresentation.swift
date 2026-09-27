import SwiftUI

struct IdentityHeading: View {
    let name: String
    let job: String
    let introduction: String
    var body: some View {
        HStack(alignment: .center, spacing: 18) {
            Text(name.isEmpty ? "?" : String(name.prefix(1))).font(.largeTitle.bold())
                .foregroundStyle(DearbyStyle.teal).frame(width: 82, height: 82)
                .background(DearbyStyle.teal.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 7) {
                Text(name.isEmpty ? "내 이름을 입력해 주세요" : name).font(.title2.bold())
                if !introduction.isEmpty { Text(introduction).font(.subheadline) }
                if !job.isEmpty { Text(job).font(.subheadline).foregroundStyle(.secondary) }
            }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct HistoryTimeline: View {
    let histories: [HistoryModel]
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(histories) { item in
                HStack(alignment: .top, spacing: 18) {
                    VStack(spacing: 0) {
                        Circle().fill(DearbyStyle.teal).frame(width: 9, height: 9)
                        Rectangle().fill(DearbyStyle.line).frame(width: 1)
                    }.padding(.top, 5)
                    VStack(alignment: .leading, spacing: 5) {
                        Text(item.startDate + (item.endDate.map { " – " + $0 } ?? ""))
                            .font(.caption).foregroundStyle(.secondary)
                        Text(item.title).font(.headline)
                        Text(item.role).font(.subheadline).foregroundStyle(.secondary)
                        if !item.description.isEmpty { Text(item.description).font(.footnote).foregroundStyle(.secondary) }
                    }.padding(.bottom, 22).frame(maxWidth: .infinity, alignment: .leading)
                }.fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

struct ContactIcons: View {
    let contacts: [ContactModel]
    let action: (ContactModel) -> Void
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 75), alignment: .center)], spacing: 16) {
            ForEach(contacts) { contact in
                Button { action(contact) } label: {
                    VStack(spacing: 9) {
                        Image(systemName: contact.symbol).font(.system(size: 28)).foregroundStyle(DearbyStyle.teal)
                        Text(contact.displayLabel).font(.caption).foregroundStyle(.primary)
                    }.frame(maxWidth: .infinity, minHeight: 68)
                }.buttonStyle(.plain).accessibilityLabel(contact.displayLabel + ": " + contact.value)
                    .disabled({ if case .unavailable = contact.action { true } else { false } }())
            }
        }
    }
}
