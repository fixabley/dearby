import Foundation
import Harmonize

/// Syntax rules only. Cross-file name resolution remains in check_fsd_boundaries.py.
enum ArchitectureRules {
    struct Violation: Equatable, CustomStringConvertible {
        let rule: String
        let path: String
        let detail: String
        var description: String { "\(path): [\(rule)] \(detail)" }
    }

    static func check(path: String, text: String) -> [Violation] {
        let source = SwiftSourceCode(source: text)
        let parts = path.split(separator: "/").map(String.init)
        let stem = URL(fileURLWithPath: path).deletingPathExtension().lastPathComponent
        let structures = source.structs(includeNested: false)
        let classes = source.classes(includeNested: false)
        let enums = source.enums(includeNested: false)
        let isWidget = parts.first == "Widgets"
        let isPresentation = isWidget || parts.first == "Pages"
        let isView = source.structs().contains { $0.inheritanceTypesNames.contains(where: isViewType) }
            || source.classes().contains { $0.inheritanceTypesNames.contains(where: isViewType) }
            || source.enums().contains { $0.inheritanceTypesNames.contains(where: isViewType) }
            || source.extensions().contains { $0.inheritanceTypesNames.contains(where: isViewType) }
        let rendering = parts.contains("UI") || (isWidget && isView)
        var result: [Violation] = []
        func reject(_ rule: String, _ detail: String) {
            result.append(Violation(rule: rule, path: path, detail: detail))
        }
        for item in source.imports() {
            let module = item.name.split(separator: ".").first.map(String.init) ?? item.name
            if parts.first == "Entities", parts.contains("Model"), module == "SwiftData" {
                reject("domain-import", "domain Model must not import \(item.name)")
            }
            if rendering, ["SwiftData", "EventKit", "EventKitUI", "MapKit", "CoreLocation", "UIKit"].contains(module) {
                reject("ui-import", "rendering UI must not import \(item.name)")
            }
        }
        // Match declarations as well as filenames, so renaming a file cannot evade location checks.
        let names = structures.map(\.name) + classes.map(\.name) + enums.map(\.name)
        for name in names where isPresentation && name.hasSuffix("State") {
            if !structures.contains(where: { $0.name == name }) {
                reject("state-struct", "\(name) must be a value struct")
            }
            let correctLocation = isWidget ? parts.count == 4 : parts.count >= 4 && parts[2] == "Model"
            if !correctLocation { reject("state-location", "\(name) belongs beside its widget or in Pages/Slice/Model") }
        }
        if isPresentation && stem.hasSuffix("State") {
            if !structures.contains(where: { $0.name == stem }) {
                reject("state-name", "file must declare struct \(stem)")
            }
            for name in names where !name.hasSuffix("State") {
                reject("state-name", "presentation values in a State file must use the State suffix: \(name)")
            }
        }
        for name in names where isPresentation && name.hasSuffix("ViewModel") {
            if !classes.contains(where: { $0.name == name }) {
                reject("viewmodel-class", "\(name) must be a class")
            }
            let correctLocation = isWidget ? parts.count == 4 : parts.count >= 4 && parts[2] == "Model"
            if !correctLocation { reject("viewmodel-location", "\(name) belongs beside its widget or in Pages/Slice/Model") }
        }
        if isPresentation && stem.hasSuffix("ViewModel"), !classes.contains(where: { $0.name == stem }) {
            reject("viewmodel-name", "file must declare class \(stem)")
        }
        if path.hasPrefix("Shared/UI/") {
            for item in structures where item.modifiers.contains(.public) || item.modifiers.contains(.open) || item.modifiers.contains(.package) {
                reject("shared-access", "\(item.name) must remain internal to the app module")
            }
            for item in classes where item.modifiers.contains(.public) || item.modifiers.contains(.open) || item.modifiers.contains(.package) {
                reject("shared-access", "\(item.name) must remain internal to the app module")
            }
            for item in enums where item.modifiers.contains(.public) || item.modifiers.contains(.open) || item.modifiers.contains(.package) {
                reject("shared-access", "\(item.name) must remain internal to the app module")
            }
        }
        return result
    }

    private static func isViewType(_ name: String) -> Bool { name == "View" || name == "SwiftUI.View" }
}
