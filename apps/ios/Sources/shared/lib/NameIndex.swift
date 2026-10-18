import Foundation

/// Groups names by their first letter for an indexed list (contract "받은 명함 목록 표시"):
/// Hangul syllables by 초성 (된소리 folded into the base consonant), Latin by upper-case A–Z, anything else under `#`.
enum NameIndex {
    static let hangul = ["ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"]
    static let latin = (UnicodeScalar("A").value...UnicodeScalar("Z").value).map { String(UnicodeScalar($0)!) }
    static let other = "#"
    /// Group order: ㄱ…ㅎ, A…Z, `#`.
    static let order: [String] = hangul + latin + [other]
    // 19 Unicode 초성 slots mapped onto the 14 base consonants (ㄲ→ㄱ, ㄸ→ㄷ, ㅃ→ㅂ, ㅆ→ㅅ, ㅉ→ㅈ).
    private static let choseong = ["ㄱ", "ㄱ", "ㄴ", "ㄷ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅂ", "ㅅ", "ㅅ", "ㅇ", "ㅈ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"]

    static func key(for name: String) -> String {
        guard let scalar = name.trimmingCharacters(in: .whitespacesAndNewlines).unicodeScalars.first else { return other }
        let value = scalar.value
        if (0xAC00...0xD7A3).contains(value) { return choseong[Int((value - 0xAC00) / 588)] }
        if let index = hangul.firstIndex(of: String(scalar)) { return hangul[index] }
        let upper = String(scalar).uppercased()
        return latin.contains(upper) ? upper : other
    }

    /// Non-empty groups in `order`, each sorted by name.
    static func sections<T>(_ items: [T], name: (T) -> String) -> [(key: String, items: [T])] {
        let grouped = Dictionary(grouping: items) { key(for: name($0)) }
        return order.compactMap { key in
            guard let group = grouped[key], !group.isEmpty else { return nil }
            return (key, group.sorted { name($0).localizedStandardCompare(name($1)) == .orderedAscending })
        }
    }
}
