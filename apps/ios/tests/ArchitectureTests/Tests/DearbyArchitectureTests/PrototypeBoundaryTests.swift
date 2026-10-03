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
            for file in files {
                let source = FSDBoundaries.File(path: file.path, text: try String(contentsOf: file, encoding: .utf8))
                #expect(source.references.isDisjoint(with: forbidden), "Unexpected service/device access in \(file.lastPathComponent)")
            }
            for name in ["Info.plist", "Info-Debug.plist"] {
                let data = try Data(contentsOf: SourceInventory.iosRoot.appendingPathComponent(name))
                let plist = try #require(PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any])
                #expect(!plist.keys.contains { $0.hasSuffix("UsageDescription") || $0.hasPrefix("Dearby") || $0 == "NSAppTransportSecurity" || $0 == "CFBundleURLTypes" })
            }
        }
    }
}
