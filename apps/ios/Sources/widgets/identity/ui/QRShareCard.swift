import SwiftUI

/// 내 코드 화면의 QR 공유 카드. 공유 URL이 준비되면 QR과 주소를 보여 주고, 공유 버튼은 같은 URL로 OS 공유 시트를 바로 연다.
/// 공유 만들기·로그인·활동 선택 반영은 화면이 맡는다. `url`이 없으면 만드는 중, `errorMessage`가 있으면 실패 상태다.
struct QRShareCard: View {
    let name: String
    let job: String
    let url: URL?
    var errorMessage: String?
    var retry: () -> Void = {}
    var activities: [DearbyChoice] = []
    var selectedActivityIDs: Binding<Set<String>> = .constant([])
    @State private var showActivities = false
    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 6) {
                DearbyAvatar(name: name, filled: true, size: 56)
                Text(name).font(.dearby(.title3).bold()).foregroundStyle(DearbyStyle.ink)
                if !job.isEmpty { Text(job).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet) }
                code.frame(width: 220, height: 220).padding(.top, 8)
                if let url {
                    Text(url.absoluteString).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                        .lineLimit(1).truncationMode(.middle).textSelection(.enabled)
                }
            }.padding(20).frame(maxWidth: .infinity)
                .background(.white, in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(DearbyStyle.line))
            if let url {
                ShareLink(item: url) {
                    Label("공유", systemImage: "square.and.arrow.up")
                }.buttonStyle(DearbyButtonStyle()).accessibilityHint("메시지·메일 등으로 명함 주소를 보내요")
            }
            if !activities.isEmpty { activityPicker }
        }
    }
    @ViewBuilder private var code: some View {
        if let url {
            DearbyQRCode(text: url.absoluteString)
                .accessibilityElement().accessibilityLabel("명함 QR").accessibilityValue(url.absoluteString)
                .accessibilityAddTraits(.isImage)
        } else if let errorMessage {
            VStack(spacing: 12) {
                Text(errorMessage).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink).multilineTextAlignment(.center)
                Button("다시 시도", action: retry).buttonStyle(DearbyButtonStyle(outlined: true))
            }
        } else {
            ProgressView("QR을 만드는 중이에요").font(.dearby(.subheadline)).tint(DearbyStyle.teal)
        }
    }
    private var activityPicker: some View {
        DisclosureGroup(isExpanded: $showActivities) {
            DearbyChoiceChips(items: activities, selection: selectedActivityIDs, label: "함께 보낼 활동").padding(.top, 10)
        } label: {
            HStack {
                Text("함께 보낼 활동 (선택)").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.ink)
                Spacer()
                let count = selectedActivityIDs.wrappedValue.count
                if count > 0 { Text("\(count)개").font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.teal) }
            }.frame(minHeight: 44)
        }.tint(DearbyStyle.quiet)
    }
}

/// 스캔 화면 틀. 카메라 미리보기는 화면이 `preview`로 넣고, 사진에서 고르는 대안을 함께 둔다.
struct QRScanOverlay<Preview: View>: View {
    let scanFromPhotos: () -> Void
    @ViewBuilder let preview: () -> Preview
    var body: some View {
        ZStack {
            preview()
            VStack(spacing: 20) {
                Spacer()
                RoundedRectangle(cornerRadius: 24).stroke(.white, lineWidth: 3).frame(width: 240, height: 240)
                    .accessibilityHidden(true)
                Text("명함 QR을 네모 안에 맞춰 주세요").font(.dearby(.subheadline)).foregroundStyle(.white)
                Spacer()
                Button(action: scanFromPhotos) {
                    Label("사진에서 스캔", systemImage: "photo").font(.dearby(.subheadline).weight(.semibold))
                        .padding(.horizontal, 18).frame(minHeight: 44).foregroundStyle(.white)
                        .background(DearbyStyle.teal, in: Capsule())
                }.buttonStyle(.plain).padding(.bottom, 24)
            }
        }.background(.black)
    }
}

#if DEBUG
/// 미리보기와 캡처 테스트가 함께 쓰는 예시.
struct QRShareSample: View {
    var url: URL? = URL(string: "https://example.invalid/s/6f1c2a9e-0b7d-4f3e-9a51-2c8e7d4b1a60")
    var errorMessage: String?
    @State var selected: Set<String> = ["conference"]
    @State var mode = 0
    var body: some View {
        VStack(spacing: 16) {
            DearbySegments(labels: ["내 코드", "스캔"], selection: $mode)
            QRShareCard(name: "김지민", job: "서비스 기획 · 커뮤니티", url: url, errorMessage: errorMessage,
                        activities: [.init(id: "conference", title: "Dearby 개발자 컨퍼런스"), .init(id: "camp", title: "Dearby 메이커 캠프")],
                        selectedActivityIDs: $selected)
        }.padding(20).background(DearbyStyle.muted)
    }
}

#Preview("내 코드") { QRShareSample() }
#Preview("만드는 중") { QRShareSample(url: nil) }
#Preview("스캔") { QRScanOverlay(scanFromPhotos: {}) { Color.gray } }
#endif
