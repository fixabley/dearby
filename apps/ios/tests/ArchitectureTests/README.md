# Prototype architecture gate

Harmonize 1.2.1 and SwiftSyntax 601.0.1 are pinned by Package.resolved.
Run `bash apps/ios/tests/run_architecture.sh` on macOS, separately from iOS XCTest.

The checkout-local inventory anchors on Sources/app/entrypoint/DearbyApp.swift.
The remaining app/widgets/features/entities/shared layers are required; pages
is optional because the prototype no longer needs a page composition wrapper.
Upward/distant/sibling/public-API/path/pure-UI/provider checks and negative
fixtures remain enabled. Component counts are diagnostic, not goals.
PrototypeBoundaryTests rejects production service/device-data APIs and privacy,
API origin, deep-link, or ATS settings in both plist files.
PrototypeTests and DiscoveryNavigationTests cover fixture/session state and
user flows; removed service/storage behavior is outside this prototype.
