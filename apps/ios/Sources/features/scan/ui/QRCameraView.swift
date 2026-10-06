import AVFoundation
import SwiftUI

/// Live camera preview that reports the first QR code it reads, then stops reporting until it is recreated.
/// Camera access is asked for only on the scan screen (contract #95 "받기").
struct QRCameraView: UIViewRepresentable {
    enum Access { case allowed, denied, unavailable }
    let found: (String) -> Void

    /// Asks once; later calls return the stored answer. Simulators and devices without a camera are `unavailable`.
    static func requestAccess() async -> Access {
        guard AVCaptureDevice.default(for: .video) != nil else { return .unavailable }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: return .allowed
        case .notDetermined: return await AVCaptureDevice.requestAccess(for: .video) ? .allowed : .denied
        default: return .denied
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(found: found) }
    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        context.coordinator.start(in: view)
        return view
    }
    func updateUIView(_ view: PreviewView, context: Context) {}
    static func dismantleUIView(_ view: PreviewView, coordinator: Coordinator) { coordinator.stop() }

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate, @unchecked Sendable {
        private let session = AVCaptureSession()
        private let found: (String) -> Void
        private var reported = false
        init(found: @escaping (String) -> Void) { self.found = found }

        func start(in view: PreviewView) {
            guard let device = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: device),
                  session.canAddInput(input) else { return }
            session.addInput(input)
            let output = AVCaptureMetadataOutput()
            guard session.canAddOutput(output) else { return }
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.qr]
            view.preview.session = session
            view.preview.videoGravity = .resizeAspectFill
            // startRunning blocks, so it stays off the main thread.
            DispatchQueue.global(qos: .userInitiated).async { self.session.startRunning() }
        }
        func stop() { DispatchQueue.global(qos: .userInitiated).async { self.session.stopRunning() } }
        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject], from connection: AVCaptureConnection) {
            guard !reported, let text = (objects.first as? AVMetadataMachineReadableCodeObject)?.stringValue else { return }
            reported = true
            found(text)
        }
    }
}
