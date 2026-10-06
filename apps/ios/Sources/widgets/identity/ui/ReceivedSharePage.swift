import SwiftUI

/// The card someone shared, with the activities they chose to send along (web `/s/<id>` shows the same).
struct ReceivedSharePage: View {
    @State private var model: ReceivedShareModel
    @State private var signingIn = false
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
                    CardView(card: card.cardModel) { if let url = $0.actionURL { openURL(url) } }
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
        }
        .safeAreaInset(edge: .bottom) { if model.canSave { saveBar } }
        .background(.white).navigationTitle("공유 명함").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
            .task { await model.load() }
            .sheet(isPresented: $signingIn) {
                NavigationStack { SignInSheet(account: model.account) { Task { await model.save() } } }
            }
    }
    private var saveBar: some View {
        VStack(spacing: 8) {
            switch model.saving {
            case .saved, .alreadySaved:
                Label(model.saving == .saved ? "받은 명함에 저장했어요" : "이미 받은 명함에 있어요", systemImage: "checkmark.circle")
                    .font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal).frame(maxWidth: .infinity, minHeight: 50)
            default:
                if case .failed(let text) = model.saving { Text(text).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink) }
                Button {
                    // Saving needs an account; signing in continues the save.
                    if model.account.session == nil { signingIn = true } else { Task { await model.save() } }
                } label: {
                    if model.saving == .saving { ProgressView().tint(.white) } else { Text("받은 명함에 저장") }
                }.buttonStyle(DearbyButtonStyle()).disabled(model.saving == .saving)
                if model.account.session == nil {
                    Text("저장할 때만 이메일 인증번호로 로그인해요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                }
            }
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.white)
            .overlay(alignment: .top) { Rectangle().fill(DearbyStyle.line).frame(height: 1) }
    }
    private func notice(_ title: String, _ detail: String) -> some View {
        VStack(spacing: 8) {
            Text(title).font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
            Text(detail).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
        }.frame(maxWidth: .infinity, minHeight: 200)
    }
}

extension ContactModel {
    /// Phone and email open the phone or mail app; links open only when they are https.
    var actionURL: URL? {
        switch kind {
        case "phone": URL(string: "tel:" + value.filter { $0.isNumber || $0 == "+" })
        case "email": URL(string: "mailto:" + value)
        default: URL(string: value).flatMap { $0.scheme == "https" ? $0 : nil }
        }
    }
}
