import Foundation
import Testing

extension ArchitectureTestSuite {
    struct PrototypeBoundaryTests {
        @Test func prototypeHasNoServiceOrDeviceDataAccess() throws {
            let files = try SourceInventory.productionFiles(iosRoot: SourceInventory.iosRoot)
            let forbidden: Set<String> = ["URLSession", "UserDefaults", "AppStorage", "SceneStorage", "FileManager",
                "SwiftData", "ModelContainer", "ModelContext", "EventKit", "EKEventStore", "AVFoundation",
                "AVCaptureSession", "Security", "SecItemCopyMatching", "SecItemAdd", "SecItemDelete",
                "Photos", "PhotosUI", "UIPasteboard", "CoreImage", "SafariServices", "SFSafariViewController", "WKWebView", "WebKit"]
            // #95: 공유 URL을 QR로 그리는 표시 파일만 CoreImage를, 카탈로그·로그인·명함 발행 클라이언트만 네트워크를,
            // 세션 보관 파일만 Keychain을 쓴다.
            let allowed: [String: Set<String>] = ["DearbyQRCode.swift": ["CoreImage"], "CatalogClient.swift": ["URLSession"],
                "AccountClient.swift": ["URLSession"], "SessionVault.swift": ["Security", "SecItemCopyMatching", "SecItemAdd", "SecItemDelete"]]
            for file in files {
                let source = FSDBoundaries.File(path: file.path, text: try String(contentsOf: file, encoding: .utf8))
                let blocked = forbidden.subtracting(allowed[file.lastPathComponent] ?? [])
                #expect(source.references.isDisjoint(with: blocked), "Unexpected service/device access in \(file.lastPathComponent)")
            }
            for name in ["Info.plist", "Info-Debug.plist"] {
                let data = try Data(contentsOf: SourceInventory.iosRoot.appendingPathComponent(name))
                let plist = try #require(PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any])
                // Only the build-injected connection origins (contract "연결 설정") may use the Dearby prefix.
                let origins: Set<String> = ["DearbyAPIOrigin", "DearbyWebOrigin"]
                #expect(!plist.keys.contains { $0.hasSuffix("UsageDescription") || ($0.hasPrefix("Dearby") && !origins.contains($0)) || $0 == "NSAppTransportSecurity" || $0 == "CFBundleURLTypes" })
            }
        }
    }
}
