import SwiftUI

/// Makes a real card from the saved profile: decorate, choose what to show, publish. Signing in is asked for when
/// the composer opens signed out; profile data itself is edited in 내 프로필.
struct CardComposerPage: View {
    @State private var model: CardPublishModel
    @State private var signingIn = false
    @Environment(\.dismiss) private var dismiss
    init(account: AccountViewModel, prefill: PublishedCard? = nil) { _model = State(initialValue: CardPublishModel(account: account, prefill: prefill)) }
    private var failure: String? { if case .failed(let text) = model.phase { text } else { nil } }
    var body: some View {
        Group {
            if case .published(let card) = model.phase {
                VStack(spacing: 16) {
                    Image(systemName: "checkmark.circle").font(.system(size: 56)).foregroundStyle(DearbyStyle.teal).accessibilityHidden(true)
                    Text("명함을 발행했어요").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
                    Text("\(card.profileName) · \(card.name)").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
                    Button("확인") { dismiss() }.buttonStyle(DearbyButtonStyle())
                }.padding(24).frame(maxHeight: .infinity)
            } else if model.account.session == nil {
                VStack(spacing: 16) {
                    Text("로그인하면 프로필 데이터로 명함을 꾸밀 수 있어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
                    Button("로그인") { signingIn = true }.buttonStyle(DearbyButtonStyle())
                }.padding(24).frame(maxHeight: .infinity)
            } else if let binding = Binding($model.draft) {
                CardComposer(draft: binding, publishing: model.phase == .publishing, errorMessage: failure ?? model.account.message) { publish() }
            } else if let failure {
                VStack(spacing: 16) {
                    Text(failure).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
                    Button("다시 시도") { Task { await model.load() } }.buttonStyle(DearbyButtonStyle(outlined: true))
                }.padding(24).frame(maxHeight: .infinity)
            } else {
                ProgressView("프로필을 불러오는 중이에요").frame(maxHeight: .infinity).tint(DearbyStyle.teal)
            }
        }
        .background(.white)
        .navigationTitle(model.prefill == nil ? "명함 만들기" : "명함 고치기").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        .task { await model.load() }
        .sheet(isPresented: $signingIn) { NavigationStack { SignInSheet(account: model.account) { Task { await model.load() } } } }
    }
    private func publish() {
        Task {
            await model.publish()
            if model.account.session == nil { signingIn = true }
        }
    }
}
