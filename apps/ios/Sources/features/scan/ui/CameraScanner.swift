import SwiftUI
import VisionKit
import AVFoundation

struct CameraScanner: View {
    let scanned: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var permitted = false
    @State private var message: String?
    var body: some View {
        NavigationStack {
            Group {
                if permitted && DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                    LiveScanner(scanned: { value in scanned(value); dismiss() }, failed: { message = $0 })
                } else {
                    ContentUnavailableView("카메라를 사용할 수 없습니다", systemImage: "camera",
                        description: Text(message ?? "카메라 지원과 권한을 확인하고 있습니다. 사진에서 QR 읽기도 사용할 수 있습니다."))
                }
            }.navigationTitle("명함 QR 찍기")
                .toolbar { Button("닫기") { dismiss() } }
                .task {
                    permitted = await AVCaptureDevice.requestAccess(for: .video)
                    if !permitted { message = "설정에서 카메라 접근을 허용해 주세요. 명함 ID 입력과 사진 읽기는 계속 사용할 수 있습니다." } else if !DataScannerViewController.isSupported { message = "이 기기는 실시간 QR 스캔을 지원하지 않습니다. 사진에서 QR 읽기를 이용해 주세요." }
                }
        }
    }
}
private struct LiveScanner: UIViewControllerRepresentable {
    let scanned: (String) -> Void
    let failed: (String) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(scanned: scanned) }
    func makeUIViewController(context: Context) -> DataScannerViewController {
        let controller = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .balanced, recognizesMultipleItems: false, isGuidanceEnabled: true, isHighlightingEnabled: true)
        controller.delegate = context.coordinator
        do { try controller.startScanning() } catch { failed("카메라를 시작하지 못했습니다. 사진에서 QR 읽기를 이용해 주세요.") }
        return controller
    }
    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {}
    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) { controller.stopScanning() }
    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let scanned: (String) -> Void
        private var completed = false
        init(scanned: @escaping (String) -> Void) { self.scanned = scanned }
        func dataScanner(_ scanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !completed else { return }
            for case .barcode(let code) in addedItems {
                if let payload = code.payloadStringValue { completed = true; scanner.stopScanning(); scanned(payload); return }
            }
        }
    }
}
