import SwiftUI

struct ExchangeActivityPicker: View {
    let activities: [ActivityModel]
    @Binding var state: ExchangeActivityState
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Picker("교환한 활동", selection: $state.selection) {
                Text("선택 안 함").tag("none")
                Text("직접 입력").tag("direct")
                ForEach(activities) { activity in
                    Text("\(activity.title) · \(activity.dateLabel)").tag(activity.id)
                }
            }
            if state.selection == "direct" {
                TextField("활동 이름", text: $state.label).textFieldStyle(.roundedBorder)
                if !state.isValid { Text("활동 이름은 200자 이하로 입력해 주세요.").font(.caption).foregroundStyle(.red) }
            }
            if activities.isEmpty { Text("등록 활동을 불러오면 여기에서 선택할 수 있어요.").font(.caption) }
            Text("지난 활동도 선택할 수 있어요. 선택은 실제 참가 인증을 뜻하지 않아요.")
                .font(.caption).foregroundStyle(.secondary)
        }
    }
}
