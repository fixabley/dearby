import Foundation
import Harmonize

/// Declaration rules; FSDBoundaries checks the cross-file syntax dependency graph.
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
        let isWidget = parts.first == "widgets"
        let isPresentation = isWidget || parts.first == "pages"
        let isView = source.structs().contains { $0.inheritanceTypesNames.contains(where: isViewType) }
            || source.classes().contains { $0.inheritanceTypesNames.contains(where: isViewType) }
            || source.enums().contains { $0.inheritanceTypesNames.contains(where: isViewType) }
            || source.extensions().contains { $0.inheritanceTypesNames.contains(where: isViewType) }
        let rendering = parts.contains("ui") || (isWidget && isView)
        var result: [Violation] = []
        func reject(_ rule: String, _ detail: String) {
            result.append(Violation(rule: rule, path: path, detail: detail))
        }
        if path.hasPrefix("app/providers/"), isView || source.structs().contains(where: {
            $0.inheritanceTypesNames.contains { ["ViewModifier", "UIViewRepresentable", "UIViewControllerRepresentable", "App"].contains($0) }
        }) {
            reject("provider-ui", "providers own construction/lifetime/injection, not UI implementation")
        }
        for item in source.imports() {
            let module = item.name.split(separator: ".").first.map(String.init) ?? item.name
            if parts.first == "entities", parts.contains("model"), module == "SwiftData" {
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
            let correctLocation = parts.count >= 4 && parts[2] == "model"
            if !correctLocation { reject("state-location", "\(name) belongs in its slice Model segment") }
        }
        if isPresentation && stem.hasSuffix("State") {
            if !structures.contains(where: { $0.name == stem }) {
                reject("state-name", "file must declare struct \(stem)")
            }
        }
        for name in names where isPresentation && name.hasSuffix("ViewModel") {
            if !classes.contains(where: { $0.name == name }) && !structures.contains(where: { $0.name == name }) {
                reject("viewmodel-type", "\(name) must be a class or struct matching its ownership")
            }
            let correctLocation = parts.count >= 4 && parts[2] == "model"
            if !correctLocation { reject("viewmodel-location", "\(name) belongs in its slice Model segment") }
        }
        if isPresentation && stem.hasSuffix("ViewModel"),
           !classes.contains(where: { $0.name == stem }) && !structures.contains(where: { $0.name == stem }) {
            reject("viewmodel-name", "file must declare class or struct \(stem)")
        }
        if path.hasPrefix("shared/ui/") {
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
