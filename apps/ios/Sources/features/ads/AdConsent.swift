import SwiftUI

/// The signed-in account's 광고 동의 calls, built by the app with the account's token refresh.
struct AdConsentServer: Sendable {
    /// `agreed` for the current version, and the account id (the access token's `sub`) that keys a refusal on this device.
    let load: @Sendable () async throws -> (agreed: Bool, account: String)
    let agree: @Sendable () async throws -> Void
    let withdraw: @Sendable () async throws -> Void
}

/// 광고 목적 개인정보 제3자 제공 동의(선택) (contract 2026-10-25): asked just in time, right before the queue's first ad request, and switched in
/// 설정 › 개인정보. Nothing about ads starts before `agreed`: no SDK start, no ATT prompt, no request. A refusal (button or closing the sheet) is
/// kept on this device per account and version, never on the server; a failed read counts as not agreed.
@MainActor @Observable final class AdConsentModel {
    enum Place { case queue, settings }
    /// The server's answer for the 설정 switch; nil before it was read, after a failed read and signed out.
    private(set) var agreed: Bool?
    /// Where the sheet is up: each screen presents its own, so a queue over 설정 never shows two.
    private(set) var asking: Place?
    /// A one-shot message after a change could not be saved.
    var notice: String?
    let privacyPolicy: URL
    @ObservationIgnored private var server: AdConsentServer?
    @ObservationIgnored private var account = ""
    @ObservationIgnored private var answer: CheckedContinuation<Bool, Never>?
    init(webOrigin: URL) {
        privacyPolicy = webOrigin.appending(path: "privacy")
    }

    func signedIn(_ server: AdConsentServer) {
        self.server = server
        agreed = nil
    }
    func signedOut() {
        server = nil
        agreed = nil
        respond(false)
    }
    /// The queue after its response: `agreed` requests the ad now; `askAtEnd` (not agreed, no refusal on this device) waits until the last
    /// activity is passed, where the ad would go (2026-10-26 user decision), and asks then; `none` neither (no ads in this build, a failed read
    /// or a remembered refusal).
    enum QueueAnswer { case agreed, askAtEnd, none }
    func queueAnswer() async -> QueueAnswer {
        guard QueueAdLoader.enabled, let agreed = await refresh() else { return .none }
        return agreed ? .agreed : declined ? .none : .askAtEnd
    }
    /// The sheet over the queue's end page; true once agreed and saved.
    func askAtQueueEnd() async -> Bool { await ask(.queue) }
    /// Reads the answer; nil when it could not be read.
    @discardableResult func refresh() async -> Bool? {
        guard let server, let state = try? await server.load() else { return nil }
        account = state.account
        agreed = state.agreed
        return state.agreed
    }
    /// 설정 switch on: the same sheet, saved on 동의하고 계속.
    func turnOn() async { _ = await ask(.settings) }
    /// 설정 switch off: withdrawn at once and remembered as a refusal, so the next queue does not ask again. An SDK already started stays until
    /// the app ends (it has no stop), and the queue's ad loader goes with the queue, which cannot be open over 설정.
    func turnOff() async {
        guard let server else { return }
        do {
            try await server.withdraw()
            agreed = false
            declined = true
        } catch { notice = "광고 동의를 철회하지 못했어요. 잠시 뒤 다시 시도해 주세요." }
    }
    /// The sheet's buttons; closing the sheet is `false`.
    func respond(_ agreed: Bool) {
        asking = nil
        answer?.resume(returning: agreed)
        answer = nil
    }

    /// Shows the sheet where asked and waits; 동의하고 계속 saves (PUT), and a failed save is "not agreed" without remembering a refusal.
    private func ask(_ place: Place) async -> Bool {
        respond(false)
        asking = place
        let yes = await withCheckedContinuation { answer = $0 }
        guard yes else {
            declined = true
            return false
        }
        guard let server else { return false }
        do {
            try await server.agree()
            agreed = true
            declined = false
            return true
        } catch {
            notice = "광고 동의를 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요."
            return false
        }
    }
    private var declinedKey: String { "adConsentDeclined.\(account).\(ConsentVersions.adThirdParty)" }
    private var declined: Bool {
        get { UserDefaults.standard.bool(forKey: declinedKey) }
        set { UserDefaults.standard.set(newValue, forKey: declinedKey) }
    }
}

/// The consent sheet (contract "시트 문구 요소", wording pending the user's check before release): title, one line, the five items, the right
/// to refuse, then `동의하고 계속` and `동의하지 않음` at the same size and weight. Closing it by pulling down is a refusal.
struct AdConsentSheet: View {
    let model: AdConsentModel
    @State private var opening: URL?
    private static let googlePolicy = URL(string: "https://policies.google.com/privacy")!
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("[선택]").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                    Text("광고를 보여 드려도 될까요?").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
                    Text("맞춤 대기열 끝에 광고 1장을 보여 드리려면 아래 정보를 Google에 제공해야 해요. 맞춤 광고는 아니에요.")
                        .foregroundStyle(.secondary)
                }.fixedSize(horizontal: false, vertical: true)
                VStack(alignment: .leading, spacing: 12) {
                    item("제공받는 자", "Google LLC(AdMob)")
                    item("제공 목적", "앱 안 광고 표시(비개인화), 같은 광고 노출 횟수 제한, 광고 성과 측정, 부정 클릭 방지")
                    item("제공 항목", "광고 식별자(추적을 허용하면 IDFA, 그 밖에는 IDFV), IP 주소(대략적 위치 추정), 기기 정보(모델·OS·언어), "
                         + "광고 상호작용(노출·클릭·동영상 재생), 진단 정보")
                    item("보유·이용 기간", "제공받는 자(Google)의 개인정보처리방침에 따름")
                    item("국외 이전", "미국(Google LLC), 광고를 요청할 때 네트워크로 전송")
                }
                .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground), in: .rect(cornerRadius: 14, style: .continuous))
                Text("동의하지 않을 수 있어요. 동의하지 않아도 모든 기능을 그대로 쓸 수 있고, 광고가 표시되지 않을 뿐이에요.")
                    .font(.dearby(.subheadline)).fixedSize(horizontal: false, vertical: true)
                VStack(alignment: .leading, spacing: 0) {
                    link("전문 보기", model.privacyPolicy)
                    link("Google 개인정보처리방침", Self.googlePolicy)
                }
            }
            .padding(.horizontal, 24).padding(.top, 28).padding(.bottom, 12)
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 8) {
                Button { model.respond(true) } label: { Text("동의하고 계속").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).tint(DearbyStyle.tealFill).accessibilityIdentifier("ad-consent-agree")
                Button { model.respond(false) } label: { Text("동의하지 않음").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered).accessibilityIdentifier("ad-consent-decline")
            }
            .controlSize(.large).font(.dearby(.body).weight(.semibold))
            .padding(.horizontal, 24).padding(.vertical, 12).background(.bar)
        }
        .font(.dearby(.body))
        .presentationDetents([.large])
        .sheet(item: $opening) { url in SafariView(url: url).ignoresSafeArea() }
    }
    private func item(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.secondary)
            Text(value).font(.dearby(.subheadline)).fixedSize(horizontal: false, vertical: true)
        }.accessibilityElement(children: .combine)
    }
    private func link(_ title: String, _ url: URL) -> some View {
        Button { opening = url } label: {
            HStack {
                Text(title)
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
            }.frame(minHeight: 44).contentShape(Rectangle())
        }.buttonStyle(.plain).foregroundStyle(DearbyStyle.teal)
    }
}

extension View {
    /// The consent sheet when `model` asks at `place`.
    func adConsentSheet(_ model: AdConsentModel?, at place: AdConsentModel.Place) -> some View {
        sheet(isPresented: Binding(get: { model?.asking == place }, set: { if !$0 { model?.respond(false) } })) {
            if let model { AdConsentSheet(model: model) }
        }
    }
}
