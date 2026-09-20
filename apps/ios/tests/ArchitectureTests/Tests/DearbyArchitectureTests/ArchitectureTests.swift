import Foundation
import Harmonize
import Testing

extension ArchitectureTestSuite {
    struct ArchitectureTests {
        @Test func productionArchitecture() throws {
            let root = SourceInventory.iosRoot.appendingPathComponent("Dearby").path + "/"
            let files = try SourceInventory.productionFiles(iosRoot: SourceInventory.iosRoot)
            #expect(files.count > 0)
            var stateCount = 0
            var viewModelCount = 0
            var sharedUICount = 0
            var domainCount = 0
            for file in files {
                let path = String(file.path.dropFirst(root.count))
                let text = try String(contentsOf: file, encoding: .utf8)
                let source = SwiftSourceCode(source: text)
                stateCount += source.structs().filter { $0.name.hasSuffix("State") }.count
                viewModelCount += (source.classes().map(\.name) + source.structs().map(\.name))
                    .filter { $0.hasSuffix("ViewModel") }.count
                if path.hasPrefix("shared/ui/") { sharedUICount += 1 }
                if path.hasPrefix("entities/") && path.contains("/model/") { domainCount += 1 }
                let violations = ArchitectureRules.check(path: path, text: text)
                #expect(violations.isEmpty, "\(violations.map(\.description).joined(separator: "\n"))")
            }
            #expect(stateCount > 0)
            #expect(viewModelCount > 0)
            #expect(sharedUICount > 0)
            #expect(domainCount > 0)
            print("Harmonize production: \(files.count) Swift files, \(stateCount) State structs, \(viewModelCount) ViewModel declarations")
        }

        struct Fixture: Sendable {
            let rule: String
            let path: String
            let valid: String
            let invalid: String
        }

        static let fixtures: [Fixture] = [
            .init(rule: "domain-import", path: "entities/notice/model/NoticeModel.swift",
                  valid: "import Foundation\nstruct NoticeModel {} // import SwiftData",
                  invalid: "import class SwiftData.ModelContext\nstruct NoticeModel {}"),
            .init(rule: "ui-import", path: "widgets/card/ui/Card.swift",
                  valid: "import SwiftUI\nstruct Card<C: View>: SwiftUI.View where C: Equatable {}",
                  invalid: "import EventKit\nstruct Card<C: View>: SwiftUI.View where C: Equatable {}"),
            .init(rule: "ui-import", path: "pages/detail/ui/DetailView.swift",
                  valid: "import SwiftUI\nstruct DetailView: View {}",
                  invalid: "#if os(iOS)\nimport SwiftData\n#endif\nstruct DetailView: View {}"),
            .init(rule: "state-struct", path: "widgets/card/model/CardState.swift",
                  valid: "struct CardState {}", invalid: "class CardState {}"),
            .init(rule: "state-location", path: "pages/detail/ui/Renamed.swift",
                  valid: "struct DetailView: View {}", invalid: "struct DetailState {}"),
            .init(rule: "state-location", path: "widgets/card/ui/model/Renamed.swift",
                  valid: "struct Helper {}", invalid: "struct CardState {}"),
            .init(rule: "state-name", path: "pages/detail/model/DetailState.swift",
                  valid: "struct DetailState {}\nstruct Place {}\nenum Availability { case ready }",
                  invalid: "struct Detail {}\nstruct Place {}"),
            .init(rule: "state-name", path: "widgets/card/model/CardState.swift",
                  valid: "struct CardState {}", invalid: "// struct CardState {}\nstruct WrongState {}"),
            .init(rule: "viewmodel-type", path: "widgets/card/model/CardViewModel.swift",
                  valid: "struct CardViewModel {}", invalid: "enum CardViewModel {}"),
            .init(rule: "viewmodel-location", path: "pages/detail/ui/Renamed.swift",
                  valid: "struct DetailView: View {}", invalid: "final class DetailViewModel {}"),
            .init(rule: "viewmodel-name", path: "pages/detail/model/DetailViewModel.swift",
                  valid: "final class DetailViewModel {}", invalid: "final class WrongViewModel {}"),
            .init(rule: "shared-access", path: "shared/ui/buttons/PrimaryButton.swift",
                  valid: "struct PrimaryButton<L: View>: View {}",
                  invalid: "public struct PrimaryButton<L: View>: View {}"),
        ]

        @Test(arguments: fixtures) func supportedRuleSensitivity(fixture: Fixture) {
            #expect(ArchitectureRules.check(path: fixture.path, text: fixture.valid).isEmpty)
            let violations = ArchitectureRules.check(path: fixture.path, text: fixture.invalid)
            #expect(violations.contains { $0.rule == fixture.rule }, "Expected \(fixture.rule); got \(violations)")
        }

        @Test func viewModelOwnershipCanBeValueOrReference() {
            for declaration in ["struct CardViewModel {}", "@Observable final class CardViewModel {}"] {
                #expect(ArchitectureRules.check(path: "widgets/card/model/CardViewModel.swift", text: declaration).isEmpty)
                #expect(ArchitectureRules.check(path: "widgets/card/ui/Renamed.swift", text: declaration)
                    .contains { $0.rule == "viewmodel-location" })
                #expect(ArchitectureRules.check(path: "widgets/card/model/OtherViewModel.swift", text: declaration)
                    .contains { $0.rule == "viewmodel-name" })
            }
            #expect(ArchitectureRules.check(path: "widgets/card/model/CardViewModel.swift", text: "actor CardViewModel {}")
                .contains { $0.rule == "viewmodel-name" })
        }

        @Test func commentsAndStringsAreNotDeclarationsOrImports() {
            let text = #"""
            import SwiftUI
            /* public struct Fake {} */
            struct Label: View {
                let text = "import UIKit; public class Fake {}"
            }
            """#
            #expect(ArchitectureRules.check(path: "shared/ui/Label.swift", text: text).isEmpty)
        }

        @Test(arguments: ["class Card: View {}", "enum Card: SwiftUI.View {}", "extension Card: View {}"])
        func alternateWidgetViewDeclarations(declaration: String) {
            #expect(ArchitectureRules.check(path: "widgets/card/ui/Card.swift", text: declaration).isEmpty)
            #expect(ArchitectureRules.check(path: "widgets/card/ui/Card.swift", text: "import EventKit\n" + declaration)
                .contains { $0.rule == "ui-import" })
        }

        @Test func genericConstraintAloneDoesNotMakeWidgetAView() {
            let source = "import EventKit\nstruct GenericState<Content: View> {}"
            #expect(ArchitectureRules.check(path: "widgets/card/model/GenericState.swift", text: source).isEmpty)
        }

        @Test func inventoryIsCheckoutLocalAndFailsClosed() throws {
            let manager = FileManager.default
            let temporary = manager.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            defer { try? manager.removeItem(at: temporary) }
            let ios = temporary.appendingPathComponent("checkout/apps/ios").standardizedFileURL
            func write(_ relative: String, _ text: String = "struct Fixture {}") throws {
                let file = temporary.appendingPathComponent(relative)
                try manager.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
                try text.write(to: file, atomically: true, encoding: .utf8)
            }
            try write("checkout/apps/ios/ARCHITECTURE.md", "fixture")
            try write("checkout/apps/ios/Dearby/app/entrypoint/DearbyApp.swift")
            try write("checkout/apps/ios/tests/ArchitectureTests/Fixtures/Bad.swift")
            try write("checkout/apps/ios/tests/ArchitectureTests/.build/checkouts/Harmonize/Bad.swift")
            try write("checkout/dearby-ios/apps/ios/Dearby/Bad.swift")
            try write("other-worktree/apps/ios/Dearby/Bad.swift")
            #expect(throws: (any Error).self) { try SourceInventory.productionFiles(iosRoot: ios) }
            for layer in ["pages", "widgets", "features", "entities", "shared"] {
                try write("checkout/apps/ios/Dearby/\(layer)/Fixture.swift")
            }
            let files = try SourceInventory.productionFiles(iosRoot: ios)
            #expect(files.count == 6)
            #expect(files.allSatisfy { $0.path.hasPrefix(ios.appendingPathComponent("Dearby").path + "/") })
            #expect(throws: (any Error).self) { try SourceInventory.productionFiles(iosRoot: temporary) }
            // Empty and non-Swift directories must not evade physical inventory checks.
            for relative in ["Uppercase", "shared/ui/Uppercase", "resources/Uppercase"] {
                let directory = ios.appendingPathComponent("Dearby/" + relative)
                try manager.createDirectory(at: directory, withIntermediateDirectories: true)
                #expect(throws: SourceInventory.InventoryError.self) { try SourceInventory.productionFiles(iosRoot: ios) }
                try manager.removeItem(at: directory)
            }
            try write("checkout/apps/ios/Dearby/Assets.xcassets/AppIcon.appiconset/Contents.json", "{}")
            #expect(try SourceInventory.productionFiles(iosRoot: ios).count == 6)
            try manager.createSymbolicLink(at: ios.appendingPathComponent("Dearby/External"), withDestinationURL: temporary.appendingPathComponent("other-worktree"))
            #expect(throws: (any Error).self) { try SourceInventory.productionFiles(iosRoot: ios) }
        }
    }
}
