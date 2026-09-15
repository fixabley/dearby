import SwiftUI

struct SettingsView: View {
    let connection: CalendarConnectionState
    let isEnabled: Bool
    let onToggle: (Bool) -> Void
    let onSettings: () -> Void
    var body: some View {
        Form {
            Section {
                CalendarConnectionControl(state: connection, isEnabled: isEnabled,
                    onToggle: onToggle, onSettings: onSettings)
            } footer: {
                Text("설정은 다음 실행에도 유지됩니다. 끄면 읽어 온 바쁜 시간을 제거합니다. 기기 캘린더 권한 자체를 철회하는 것은 아닙니다.")
            }
        }
        .navigationTitle("환경설정")
        .navigationBarTitleDisplayMode(.inline)
    }
}
