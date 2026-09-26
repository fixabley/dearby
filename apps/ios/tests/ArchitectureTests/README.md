# Restored native architecture gate

Restored from pre-web commit f1d9a63 for the 2026-09-27 native slice. Harmonize 1.2.1 and SwiftSyntax 601.0.1 remain pinned by Package.resolved. Tests run on host macOS, separately from the iOS app targets.

Run `bash apps/ios/tests/run_architecture.sh`. Production inventory is checkout-local Sources/ with app/entrypoint/DearbyApp.swift as its anchor. Existing upward/distant/sibling/API/path/pure-UI/provider rules and their negative fixtures remain. Old positive counts requiring deleted Notice ViewModels/shared UI are diagnostics now; no artificial types are added to satisfy component counts. Calendar/cache-specific Python fixtures referenced deleted product code and are not part of this slice; app persistence failure behavior has current XCTest coverage.
