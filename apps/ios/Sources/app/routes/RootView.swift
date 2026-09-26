import SwiftUI

struct RootView: View {
    @Bindable var state: AppState
    @State private var sheet: RootSheet?
    @State private var importAfterLogin = false
    var body: some View {
        HomePage(selectedTab: $state.activeTab, configured: state.api.baseURL != nil,
            qr: QRPage(cards: state.cards, incomingURL: state.incomingURL, selectedID: $state.selectedCardID,
                create: openComposer, lookup: state.resolveCard, saveGuest: state.saveGuest),
            wallet: WalletPage(receipts: state.receipts, guests: state.guests,
                importAction: { if state.session == nil { sheet = .login } else { state.showImport = true } },
                send: { state.recipientID = $0.card.ownerId; sheet = .send },
                refresh: { await state.perform { try await state.refresh() } }),
            profile: ProfilePage(isAuthenticated: state.session != nil, profile: state.profile, save: state.saveProfile, account: account))
        .sheet(item: $sheet, onDismiss: {
            if importAfterLogin { state.showImport = true; importAfterLogin = false }
        }) { destination in
            switch destination {
            case .login: LoginView(state: state.loginState, login: { challenge, code in
                try await state.login(challengeID: challenge, code: code)
                importAfterLogin = !state.guests.isEmpty
            })
            case .compose: CardComposer(profile: state.profile, publish: state.publish)
            case .send:
                if let recipient = state.recipient { SendCardView(recipient: recipient, cards: state.cards, profile: state.profile,
                    publish: state.publish, send: { id, context in
                        try await state.send(cardID: id, recipientID: recipient.ownerId, context: context)
                    }) }
            }
        }
        .sheet(isPresented: $state.showImport) {
            GuestImportView(guests: state.guests, resolve: state.resolveCard, importAction: state.importGuests)
        }
        .alert("알림", isPresented: Binding(get: { state.message != nil }, set: { if !$0 { state.message = nil } })) {
            Button("확인") { state.message = nil }
        } message: { Text(state.message ?? "") }
        .onOpenURL { state.receiveURL($0) }
        .task { if state.session != nil { await state.perform { try await state.refresh() } } }
    }
    @ViewBuilder private var account: some View {
        if state.session == nil { Button("이메일 로그인") { sheet = .login } } else {
            Button("명함 새로고침") { Task { await state.perform { try await state.refresh() } } }
            Button("로그아웃") { Task { await state.perform { try await state.logout() } } }
        }
    }
    private func openComposer() { sheet = state.session == nil ? .login : .compose }
}
private enum RootSheet: Identifiable {
    case login, compose, send
    var id: String {
        switch self { case .login: "login"; case .compose: "compose"; case .send: "send" }
    }
}
