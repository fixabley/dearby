import Foundation

struct NoticeEvidence: Codable {
    let sourceId: String
    let locator: String
    let fieldPath: String
    var sourceURL: URL? = nil
}

/// Reads evidence at arbitrary nested contract fields without retaining an untyped JSON object.
enum NoticeEvidenceDecoder {
    private struct Key: CodingKey {
        let stringValue: String
        var intValue: Int? { nil }
        init(stringValue: String) { self.stringValue = stringValue }
        init?(intValue: Int) { return nil }
    }

    static func collect(from decoder: any Decoder, path: String = "") throws -> [NoticeEvidence] {
        if let object = try? decoder.container(keyedBy: Key.self) {
            var result: [NoticeEvidence] = []
            let declaredPath = try object.decodeIfPresent(String.self, forKey: Key(stringValue: "fieldPath"))
            for key in object.allKeys.sorted(by: { $0.stringValue < $1.stringValue }) {
                if key.stringValue == "evidence" || key.stringValue == "coordinateEvidence" {
                    var references = try object.nestedUnkeyedContainer(forKey: key)
                    while !references.isAtEnd {
                        let reference = try references.nestedContainer(keyedBy: Key.self)
                        result.append(NoticeEvidence(
                            sourceId: try reference.decode(String.self, forKey: Key(stringValue: "sourceId")),
                            locator: try reference.decode(String.self, forKey: Key(stringValue: "locator")),
                            fieldPath: declaredPath ?? (key.stringValue == "coordinateEvidence" ? path + ".coordinates" : path)))
                    }
                } else {
                    let childPath = path.isEmpty ? key.stringValue : path + "." + key.stringValue
                    result += try collect(from: object.superDecoder(forKey: key), path: childPath)
                }
            }
            return result
        }
        if var array = try? decoder.unkeyedContainer() {
            var result: [NoticeEvidence] = []
            while !array.isAtEnd {
                let index = array.currentIndex
                result += try collect(from: array.superDecoder(), path: "\(path)[\(index)]")
            }
            return result
        }
        return []
    }
}
