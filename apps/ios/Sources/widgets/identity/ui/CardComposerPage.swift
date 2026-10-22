import SwiftUI

/// Makes a real card from the saved profile: decorate, choose what to show, publish. Signing in is asked for when
/// the composer opens signed out; profile data itself is edited in 내 프로필.
/// Every state that is not the form (published, signed out, no profile yet, failed) is one centred message (contract "비스크롤 화면 배치·CTA").
struct CardComposerPage: View {
    @State private var model: CardPublishModel
    @State private var signingIn = false
    @Environment(\.dismiss) private var dismiss
    init(account: AccountViewModel, prefill: PublishedCard? = nil) { _model = State(initialValue: CardPublishModel(account: account, prefill: prefill)) }
    private var failure: String? { if case .failed(let text) = model.phase { text } else { nil } }
    var body: some View {
        Group {
            if case .published(let card) = model.phase {
                CenteredMessage(symbol: "checkmark.circle", title: model.prefill == nil ? "명함을 발행했어요" : "고친 내용을 저장했어요", message: "\(card.profileName) · \(card.name)",
                                primary: MessageAction("확인") { dismiss() })
            } else if model.account.session == nil {
                CenteredMessage(symbol: "person.text.rectangle", title: "로그인하면 명함을 만들 수 있어요", message: "프로필의 이름·연락처·이력으로 명함을 꾸며요.",
                                primary: MessageAction("로그인") { signingIn = true })
            } else if let draft = model.draft, draft.profile.version == 0 {
                // Signed in from here without ever saving a profile: the composer has no data to fill the template with.
                CenteredMessage(symbol: "person.crop.circle", title: "먼저 프로필을 적어 주세요", message: "내 프로필 탭에서 이름과 연락처를 저장하면 명함을 만들 수 있어요.",
                                primary: MessageAction("닫기") { dismiss() })
            } else if let binding = Binding($model.draft) {
                CardComposer(draft: binding, editing: model.prefill != nil, publishing: model.phase == .publishing, errorMessage: failure ?? model.account.message) { publish() }
            } else if let failure {
                CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "프로필을 불러오지 못했어요", message: failure,
                                primary: MessageAction("다시 시도") { Task { await model.load() } })
            } else {
                // Contract "내용 로딩은 스켈레톤" 2026-10-22: the form filled from a sample profile.
                CardComposer(draft: .constant(CardDraft(profile: .placeholder)), editing: model.prefill != nil) {}.dearbySkeleton()
            }
        }
        .background(Color(.systemGroupedBackground))
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
