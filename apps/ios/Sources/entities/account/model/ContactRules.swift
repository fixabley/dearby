import Foundation

/// Contract #149: a profile needs a phone and an email. Same rules as `PUT /v1/profile`.
enum ContactRules {
    /// Digits, `+`, `-` and spaces only, with 8 to 15 digits.
    static func validPhone(_ value: String) -> Bool {
        let text = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let digits = text.filter(\.isASCII).filter(\.isNumber).count
        return !text.isEmpty && text.allSatisfy { ($0.isASCII && $0.isNumber) || "+- ".contains($0) } && (8...15).contains(digits)
    }
    /// A basic `name@domain.tld` shape; the confirmation is that mail arrives.
    static func validEmail(_ value: String) -> Bool {
        value.trimmingCharacters(in: .whitespacesAndNewlines).range(of: #"^[^@\s]+@[^@\s]+\.[^@\s]+$"#, options: .regularExpression) != nil
    }
    /// `2026.03 – 2026.06`, or `2026.03 – 진행 중` when there is no end date. Dates are `YYYY-MM-DD`.
    static func period(start: String, end: String?) -> String {
        func month(_ date: String) -> String { date.prefix(7).replacingOccurrences(of: "-", with: ".") }
        return month(start) + " – " + (end.map(month) ?? "진행 중")
    }
}
