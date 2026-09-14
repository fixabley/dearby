import Foundation
import SwiftData

@Model
final class NoticeRecord {
    @Attribute(.unique) var id: String
    var payload: Data
    init(id: String, payload: Data) { self.id = id; self.payload = payload }
}
