import SwiftUI

/// The card someone shared, with the activities they chose to send along (web `/s/<id>` shows the same).
struct ReceivedSharePage: View {
    @State private var model: ReceivedShareModel
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss
    init(model: ReceivedShareModel) { _model = State(initialValue: model) }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                switch model.phase {
                case .loading:
                    ProgressView("명함을 불러오는 중이에요").frame(maxWidth: .infinity, minHeight: 240).tint(DearbyStyle.teal)
                case .missing:
                    notice("명함을 찾을 수 없어요", "공유한 사람이 명함을 거둬들였거나 잘못된 QR이에요.")
                case .failed:
                    notice("명함을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.")
                    Button("다시 시도") { Task { await model.load() } }.buttonStyle(DearbyButtonStyle(outlined: true))
                case .loaded(let card, let activities):
                    CardView(card: Self.cardModel(card)) { open($0) }
                    if !activities.isEmpty {
                        Divider()
                        Text("함께 공유된 활동").font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
                        ForEach(activities, id: \.id) { activity in
                            Label(activity.title, systemImage: "calendar").font(.dearby(.subheadline))
                        }
                        Text("공유한 사람이 고른 활동이에요. 참가 확인은 아니에요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                    }
                }
            }.padding(20)
        }.background(.white).navigationTitle("공유 명함").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
            .task { await model.load() }
    }
    private func notice(_ title: String, _ detail: String) -> some View {
        VStack(spacing: 8) {
            Text(title).font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
            Text(detail).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
        }.frame(maxWidth: .infinity, minHeight: 200)
    }
    /// Phone and email open the phone or mail app; links open only when they are https.
    private func open(_ contact: ContactModel) {
        let target: URL? = switch contact.kind {
        case "phone": URL(string: "tel:" + contact.value.filter { $0.isNumber || $0 == "+" })
        case "email": URL(string: "mailto:" + contact.value)
        default: URL(string: contact.value).flatMap { $0.scheme == "https" ? $0 : nil }
        }
        if let target { openURL(target) }
    }
    static func cardModel(_ card: PublishedCard) -> CardModel {
        CardModel(id: card.id, name: card.name, profileName: card.profileName, job: card.job, introduction: card.introduction ?? "",
                  description: card.description,
                  contacts: card.contacts.map { ContactModel(id: $0.id, kind: $0.kind, displayLabel: $0.label, value: $0.value) },
                  histories: card.histories.map { HistoryModel(id: $0.id, title: $0.title, role: $0.role, startDate: $0.startDate, endDate: $0.endDate) })
    }
}
