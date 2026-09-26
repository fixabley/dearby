// swift-tools-version: 6.1
import PackageDescription

let package = Package(
    name: "DearbyArchitectureTests",
    platforms: [.macOS(.v13)],
    dependencies: [
        .package(url: "https://github.com/perrystreetsoftware/Harmonize.git", exact: "1.2.1"),
        // Keep the parser compatible with the Swift 6.1 CI baseline.
        .package(url: "https://github.com/swiftlang/swift-syntax.git", exact: "601.0.1"),
    ],
    targets: [
        .testTarget(name: "DearbyArchitectureTests", dependencies: [
            .product(name: "Harmonize", package: "Harmonize"),
            .product(name: "SwiftParser", package: "swift-syntax"),
            .product(name: "SwiftSyntax", package: "swift-syntax"),
        ]),
    ]
)
