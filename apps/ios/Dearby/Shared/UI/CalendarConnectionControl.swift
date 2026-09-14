import SwiftUI

struct CalendarConnectionControl: View {
    let state: CalendarConnectionState
    let isEnabled: Bool
    let onToggle: (Bool) -> Void
    let onContinue: () -> Void
    let onCancel: () -> Void
    let onSettings: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: NativeSpacing.related) {
            Toggle("내 일정 표시", isOn: Binding(get: { isEnabled }, set: { onToggle($0) }))
            switch state {
            case .off, .consent:
                Text("확정된 활동의 선택 날짜에서 기기 일정과 겹치는 시간을 확인합니다. 신청기간은 제외합니다.")
                    .font(.footnote).foregroundStyle(.secondary)
            case .checking, .requesting:
                ProgressView("캘린더 연결 확인 중").font(.footnote)
            case .connected:
                Text("선택 날짜의 기기 일정 기준입니다. 겹침 없음은 참여 가능을 보장하지 않습니다.")
                    .font(.footnote).foregroundStyle(.secondary)
            case .denied:
                Text("캘린더 읽기 권한이 없습니다. 바쁜 시간을 확인하지 못했습니다.").font(.footnote)
                Button("설정 열기", action: onSettings).frame(minHeight: 44)
            case .restricted:
                Text("이 기기에서는 캘린더 접근이 제한되어 있습니다.").font(.footnote)
            case .failed:
                Text("캘린더를 연결하지 못했습니다.").font(.footnote)
                Button("다시 시도") { onToggle(true) }.frame(minHeight: 44)
            }
        }
        .alert("내 일정 연결", isPresented: Binding(get: { state == .consent }, set: { if !$0 { onCancel() } })) {
            Button("취소", role: .cancel, action: onCancel)
            Button("계속", action: onContinue)
        } message: {
            Text("캘린더의 바쁜 시간 정보를 가져와 활동 일정과 겹치는 시간을 확인합니다. 일정 제목·장소는 표시하지 않으며, 서버로 전송하지 않습니다.")
        }
    }
}
