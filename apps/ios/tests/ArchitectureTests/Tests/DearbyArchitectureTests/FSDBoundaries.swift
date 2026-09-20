import Foundation
import Harmonize
import SwiftParser
import SwiftSyntax

/// Structural declaration graph. Identifiers are parsed syntax, not text in comments/strings.
/// This intentionally does not claim type-checker resolution of aliases/inference/macros.
enum FSDBoundaries {
    static let layers = ["app", "pages", "widgets", "features", "entities", "shared"]
    struct File {
        let path: String
        let source: SwiftSourceCode
        let syntax: SourceFileSyntax
        let names: Set<String>
        let references: Set<String>
        var parts: [String] { path.split(separator: "/").map(String.init) }
        var layer: String { parts.first ?? "" }
        var slice: String { parts.prefix(layer == "app" || layer == "shared" ? 1 : 2).joined(separator: "/") }
        var pureUI: Bool { (parts.contains("ui") && ["entities", "shared"].contains(layer)) }
        init(path: String, text: String) {
            self.path = path
            source = SwiftSourceCode(source: text)
            syntax = Parser.parse(source: text)
            var declarations = Set(source.structs(includeNested: false).filter { $0.parent == nil }.map(\.name)
                + source.classes(includeNested: false).filter { $0.parent == nil }.map(\.name)
                + source.enums(includeNested: false).filter { $0.parent == nil }.map(\.name)
                + source.protocols(includeNested: false).filter { $0.parent == nil }.map(\.name))
            func collect(_ statements: CodeBlockItemListSyntax) {
              for statement in statements {
                if let conditional = statement.item.as(IfConfigDeclSyntax.self) {
                    for clause in conditional.clauses {
                        if let statements = clause.elements?.as(CodeBlockItemListSyntax.self) { collect(statements) }
                    }
                }
                if let actor = statement.item.as(ActorDeclSyntax.self) { declarations.insert(actor.name.text) }
                if let alias = statement.item.as(TypeAliasDeclSyntax.self) { declarations.insert(alias.name.text) }
                if let function = statement.item.as(FunctionDeclSyntax.self) { declarations.insert(function.name.text) }
            }
            }
            collect(syntax.statements)
            names = declarations
            references = Set(syntax.tokens(viewMode: .sourceAccurate).compactMap {
                if case .identifier(let name) = $0.tokenKind { return name }
                return nil
            })
        }
    }

    static func check(sources: [String: String], exports: [String: [String]], pureUI: [String] = []) -> [ArchitectureRules.Violation] {
        let files = sources.map { File(path: $0.key, text: $0.value) }
        var declarations: [String: [File]] = [:]
        for file in files { for name in file.names { declarations[name, default: []].append(file) } }
        var errors: [ArchitectureRules.Violation] = []
        let pureNames = Set(pureUI)
        if pureNames.count != pureUI.count {
            errors.append(.init(rule: "pure-ui-contract", path: "pure-ui.json", detail: "duplicate pure UI declarations"))
        }
        for name in pureNames {
            guard let owners = declarations[name], owners.count == 1,
                  owners[0].parts.contains("ui"), ["pages", "widgets"].contains(owners[0].layer) else {
                errors.append(.init(rule: "pure-ui-contract", path: "pure-ui.json", detail: "missing/ambiguous presentation UI: \(name)"))
                continue
            }
        }
        for (name, owners) in declarations where owners.count > 1 {
            errors.append(.init(rule: "fsd-ambiguous", path: owners[0].path, detail: "ambiguous top-level declaration \(name)"))
        }
        for (slice, names) in exports {
            for name in names where declarations[name]?.filter({ $0.slice == slice }).count != 1 {
                errors.append(.init(rule: "fsd-manifest", path: "public-api.json", detail: "\(slice) exports missing/ambiguous \(name)"))
            }
            if !files.contains(where: { $0.slice == slice }) || Set(names).count != names.count {
                errors.append(.init(rule: "fsd-manifest", path: "public-api.json", detail: "unknown slice or duplicate entries: \(slice)"))
            }
        }
        for file in files {
            func reject(_ rule: String, _ detail: String) {
                errors.append(.init(rule: rule, path: file.path, detail: detail))
            }
            guard let rank = layers.firstIndex(of: file.layer) else { reject("fsd-path", "unknown layer"); continue }
            let parts = file.parts
            for directory in parts.dropLast() where directory.range(of: "^[a-z][A-Za-z0-9]*$", options: .regularExpression) == nil {
                reject("directory-name", "source directory must use lowerCamelCase: \(directory)")
            }
            if file.layer == "app" {
                if parts.count < 3 || !["entrypoint", "routes", "providers"].contains(parts[1]) {
                    reject("fsd-path", "App requires entrypoint/routes/Providers")
                }
            } else if file.layer == "shared" {
                if parts.count < 3 || !["ui", "model", "api", "lib", "config"].contains(parts[1]) {
                    reject("fsd-path", "Shared requires purpose segment")
                }
            } else if parts.count < 4 || !["ui", "model", "api", "lib", "config"].contains(parts[2]) {
                reject("fsd-path", "expected Layer/Slice/Segment/file")
            }
            let isProvider = parts.count >= 3 && parts[0] == "app" && parts[1] == "providers"
            for item in file.source.imports() {
                let module = item.name.split(separator: ".").first.map { $0.lowercased() } ?? ""
                guard let importedRank = layers.firstIndex(of: module) else { continue }
                if importedRank < rank { reject("fsd-upward", "import \(item.name)") }
                if importedRank > rank + 2 && !isProvider { reject("fsd-distant", "import \(item.name)") }
            }
            if file.references.contains("_exported") { reject("fsd-reexport", "re-exported imports bypass slice contracts") }
            let aliasVisitor = CrossSliceAliasVisitor(declarations: declarations, slice: file.slice)
            aliasVisitor.walk(file.syntax)
            for name in aliasVisitor.forbidden { reject("fsd-alias", "cross-slice typealias to \(name)") }
            if file.syntax.hasError { reject("swift-syntax", "source must parse without errors") }
            let isPureUI = file.pureUI || !file.names.isDisjoint(with: pureNames)
            let isRoute = file.path.hasPrefix("app/routes/")
            let effects: Set<String> = ["UserDefaults", "Bundle", "FileManager", "URLSession", "UIApplication", "openURL", "SwiftData", "ModelContext", "ModelContainer", "EventKit", "EventKitUI", "EKEventStore", "EKEventEditViewController", "MapKit", "MKMapItem", "CoreLocation", "CLLocationManager", "UIKit"]
            for name in file.references {
                if isPureUI && (effects.contains(name) || name.hasSuffix("Repository") || name.hasSuffix("ViewModel")) {
                    reject("pure-ui-effect", "pure UI cannot use \(name)")
                }
                if isRoute && (effects.contains(name) || name.hasSuffix("Repository")) {
                    reject("route-effect", "routes compose pages, not storage or OS work: \(name)")
                }
            }
            for name in file.references {
                for target in declarations[name] ?? [] where target.path != file.path {
                    guard let targetRank = layers.firstIndex(of: target.layer) else { continue }
                    // Only exported Shared UI declarations: never blanket imports or Shared api/lib.
                    let sharedDesign = ["pages", "widgets"].contains(file.layer)
                        && target.path.hasPrefix("shared/ui/") && (exports["shared"] ?? []).contains(name)
                    // This route only passes the app-owned preferences into the detail Page.
                    let detailPreferences = file.path == "app/routes/NoticeDestinationView.swift"
                        && target.path == "features/checkCalendarOverlap/model/CalendarPreferences.swift"
                        && name == "CalendarPreferences"
                    if targetRank > rank + 2 && !isProvider && !sharedDesign && !detailPreferences {
                        reject("fsd-distant", "\(name) from \(target.path)")
                    }
                    if targetRank < rank { reject("fsd-upward", "\(name) from \(target.path)") }
                    if target.layer == file.layer && target.slice != file.slice {
                        reject("fsd-cross-slice", "\(name) from \(target.path)")
                    }
                    if target.slice != file.slice && !(exports[target.slice] ?? []).contains(name) {
                        reject("fsd-public-api", "\(name) is internal to \(target.slice)")
                    }
                    if isRoute && target.parts.contains("api") {
                        reject("route-effect", "routes cannot access API implementation \(name)")
                    }
                    if isPureUI && (target.parts.contains("api") || name.hasSuffix("Repository") || name.hasSuffix("ViewModel")) {
                        reject("pure-ui-effect", "pure UI cannot access \(name)")
                    }
                }
            }
        }
        return errors.sorted { $0.description < $1.description }
    }
}

/// Inspect alias RHS in conditional/nested declarations too; do not turn exports into alias facades.
private final class CrossSliceAliasVisitor: SyntaxVisitor {
    let declarations: [String: [FSDBoundaries.File]]
    let slice: String
    var forbidden = Set<String>()

    init(declarations: [String: [FSDBoundaries.File]], slice: String) {
        self.declarations = declarations
        self.slice = slice
        super.init(viewMode: .sourceAccurate)
    }

    override func visit(_ node: TypeAliasDeclSyntax) -> SyntaxVisitorContinueKind {
        for token in node.initializer.value.tokens(viewMode: .sourceAccurate) {
            if case .identifier(let name) = token.tokenKind,
               declarations[name]?.contains(where: { $0.slice != slice }) == true { forbidden.insert(name) }
        }
        return .visitChildren
    }
}
