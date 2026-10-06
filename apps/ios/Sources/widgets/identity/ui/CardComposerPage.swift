import SwiftUI

/// Makes a real card: edit the profile and choose what to show, then publish. Signing in is asked
/// for only when publishing, and publishing continues right after.
struct CardComposerPage: View {
    @State private var model: CardPublishModel
    @State private var signingIn = false
    @Environment(\.dismiss) private var dismiss
    init(account: AccountViewModel) { _model = State(initialValue: CardPublishModel(account: account)) }
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
            } else {
                CardComposer(name: $model.draft.name, job: $model.draft.job, introduction: $model.draft.introduction,
                             contacts: $model.draft.contacts, histories: $model.draft.histories,
                             requiresLogin: model.account.session == nil,
                             publishing: model.phase == .publishing || model.phase == .loading,
                             errorMessage: failure ?? model.account.message) { publish() }
            }
        }
        .background(.white)
        .navigationTitle("명함 만들기").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        .task { await model.load() }
        .sheet(isPresented: $signingIn) {
            NavigationStack { SignInSheet(account: model.account) { Task { await model.continueAfterSignIn() } } }
        }
    }
    private func publish() {
        guard model.account.session != nil else { signingIn = true; return }
        Task {
            await model.publish()
            // The session expired during publishing: sign in again and the draft is kept.
            if model.account.session == nil { signingIn = true }
        }
    }
}
