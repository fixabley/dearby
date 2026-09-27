import Foundation

struct ExchangeActivityState {
    var selection = "none"
    var label = ""
    var context: ExchangeContextModel {
        switch selection {
        case "none": return ExchangeContextModel()
        case "direct":
            let value = label.trimmingCharacters(in: .whitespacesAndNewlines)
            return ExchangeContextModel(label: value.isEmpty ? nil : value)
        default: return ExchangeContextModel(activityId: selection)
        }
    }
    var isValid: Bool { context.label?.count ?? 0 <= 200 }
    static func title(_ context: ExchangeContextModel, activities: [ActivityModel]) -> String {
        if let id = context.activityId { return activities.first { $0.id == id }?.title ?? "활동 정보 미확인" }
        return context.label ?? "활동 선택 안 함"
    }
}
