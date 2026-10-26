import FirebaseAnalytics
import FirebaseCore
import SwiftUI

/// 사용 통계 (contract `analytics-v1.md` 2026-10-25): Firebase Analytics without the advertising identifier (`FirebaseAnalyticsCore`, no ATT prompt;
/// Info.plist turns off IDFV, ad personalisation signals, ad network registration and automatic screen reporting). Events and user properties
/// carry ids and codes only — never a user id, 지원 자격 values, names, contacts or card contents. `start()` runs once at launch and only when the
/// build bundles `GoogleService-Info.plist` (gitignored, written by CI from a secret); without it every call is a no-op. Collection follows the
/// 설정 `개인정보` `사용 통계 보내기` (on by default, kept on the device).
@MainActor enum AppAnalytics {
    /// `사용 통계 보내기`, read with `@AppStorage(AppAnalytics.enabledKey)`; missing means on.
    static let enabledKey = "dearby.analytics.enabled"
    private static var started = false

    static func start() {
        guard !started, Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil else { return }
        FirebaseApp.configure()
        started = true
        Analytics.setAnalyticsCollectionEnabled(UserDefaults.standard.object(forKey: enabledKey) as? Bool ?? true)
    }
    /// The profile switch (it stores the choice itself): collection stops (or resumes) at once.
    static func setEnabled(_ on: Bool) {
        if started { Analytics.setAnalyticsCollectionEnabled(on) }
    }
    /// `parameters` hold ids, codes and counts only.
    static func event(_ name: String, _ parameters: [String: Any] = [:]) {
        guard started else { return }
        Analytics.logEvent(name, parameters: parameters.isEmpty ? nil : parameters)
    }
    /// `screen_view` with the page name (`discovery`, `activity_detail`, …).
    static func screen(_ name: String) {
        event(AnalyticsEventScreenView, [AnalyticsParameterScreenName: name, AnalyticsParameterScreenClass: name])
    }
    /// One of the four user properties (`interest_fields`, `interest_kinds`, `has_eligibility`, `signed_in`); nil clears it.
    static func userProperty(_ name: String, _ value: String?) {
        guard started else { return }
        Analytics.setUserProperty(value.map { String($0.prefix(36)) }, forName: name)
    }
    /// `activity_id` with `field` and `category` (`tags`).
    static func activity(_ id: String, _ activity: ActivityModel?) -> [String: Any] {
        tags(activity?.fieldTags ?? [], activity?.category).merging(["activity_id": id]) { $1 }
    }
    /// `field` (the first 분야 tag's English name) and `category` (the 활동 종류 code in lower case); unknown values are left out.
    static func tags(_ fieldTags: [Tag], _ category: ActivityCategory?) -> [String: Any] {
        var parameters: [String: Any] = [:]
        if let field = fieldTags.first?.nameEn, !field.isEmpty { parameters["field"] = field }
        if let category { parameters["category"] = category.rawValue.lowercased() }
        return parameters
    }
}

extension View {
    /// `screen_view` each time the page comes on screen (also back from a pushed page or another tab) or its name changes.
    func analyticsScreen(_ name: String) -> some View { task(id: name) { AppAnalytics.screen(name) } }
    /// `menu_open(menu)` once per presentation: coming back from a page pushed on top does not count again.
    func analyticsMenu(_ menu: String) -> some View { modifier(MenuOpen(menu: menu)) }
}

extension Binding where Value: Equatable {
    /// The same binding, also reporting a change made through it (a control the user touched) but not one the app made elsewhere.
    @MainActor func reporting(_ report: @escaping (Value) -> Void) -> Binding<Value> {
        Binding(get: { wrappedValue }, set: { value in
            if value != wrappedValue { report(value) }
            wrappedValue = value
        })
    }
}

private struct MenuOpen: ViewModifier {
    let menu: String
    @State private var logged = false
    func body(content: Content) -> some View {
        content.onAppear {
            guard !logged else { return }
            logged = true
            AppAnalytics.event("menu_open", ["menu": menu])
        }
    }
}
