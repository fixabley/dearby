import Foundation
import Testing

@Suite(.serialized)
struct FSDTests {
    @Test func productionBoundaries() throws {
        let ios = SourceInventory.iosRoot
        let root = ios.appendingPathComponent("Dearby").path + "/"
        let exports = try JSONDecoder().decode([String: [String]].self,
            from: Data(contentsOf: ios.appendingPathComponent("architecture/public-api.json")))
        let migrations = try JSONDecoder().decode([String: String].self,
            from: Data(contentsOf: ios.appendingPathComponent("architecture/migration-paths.json")))
        let files = try SourceInventory.productionFiles(iosRoot: ios)
        var sources: [String: String] = [:]
        for file in files {
            let path = String(file.path.dropFirst(root.count))
            let canonical = migrations[path] ?? path
            #expect(sources[canonical] == nil, "duplicate migration target \(canonical)")
            sources[canonical] = try String(contentsOf: file, encoding: .utf8)
        }
        let errors = FSDBoundaries.check(sources: sources, exports: exports)
        #expect(errors.isEmpty, "\(errors.map(\.description).joined(separator: "\n"))")
    }

    @Test func manifestAndConditionalDeclarations() {
        #expect(FSDBoundaries.check(sources: ["": "struct Empty {}"], exports: [:]).contains { $0.rule == "fsd-path" })
        #expect(FSDBoundaries.check(sources: ["Shared/Lib/A.swift": "struct A {}"], exports: ["Shared": ["Missing"]]).contains { $0.rule == "fsd-manifest" })
        #expect(FSDBoundaries.check(sources: ["Shared/Lib/A.swift": "struct A {}", "Shared/Lib/B.swift": "struct A {}"], exports: [:]).contains { $0.rule == "fsd-ambiguous" })
        for declaration in ["actor Hidden {}", "typealias Hidden = Int", "func Hidden() {}"] {
            let sources = ["Entities/Notice/API/Hidden.swift": "#if os(iOS)\n" + declaration + "\n#endif",
                           "Pages/Detail/Model/Consumer.swift": "struct Consumer { let value = Hidden() }"]
            #expect(FSDBoundaries.check(sources: sources, exports: [:]).contains { $0.rule == "fsd-public-api" })
        }
    }

    @Test func dependencyFixtures() {
        let declarations = [
            "Entities/Notice/Model/NoticeModel.swift": "struct NoticeModel {}",
            "Entities/Notice/API/NoticeRepository.swift": "class NoticeRepository {}",
            "Entities/Notice/API/NoticeRecord.swift": "struct NoticeRecord {}",
            "Entities/Notice/API/NoticeStorageCodec.swift": "enum NoticeStorageCodec {}",
            "Entities/Organization/Model/OrganizationModel.swift": "struct OrganizationModel {}",
            "Widgets/Card/Model/CardViewModel.swift": "class CardViewModel {}",
            "Widgets/Sibling/Model/SiblingViewModel.swift": "class SiblingViewModel {}",
            "Features/Save/Model/Save.swift": "class Save {}",
            "Pages/Detail/Model/DetailViewModel.swift": "class DetailViewModel {}",
            "Shared/Lib/DateValue.swift": "struct DateValue {}",
        ]
        let exports = ["Entities/Notice": ["NoticeModel", "NoticeRepository"],
                       "Entities/Organization": ["OrganizationModel"], "Widgets/Card": ["CardViewModel"],
                       "Widgets/Sibling": ["SiblingViewModel"], "Features/Save": ["Save"],
                       "Pages/Detail": ["DetailViewModel"], "Shared": ["DateValue"]]
        let allowed: [(String, String)] = [
            ("Entities/Notice/UI/Label.swift", "NoticeModel"),
            ("Widgets/Card/UI/Card.swift", "CardViewModel"),
            ("Widgets/Card/UI/CardContent.swift", "NoticeModel"),
            ("Widgets/Card/UI/Card.swift", "Save"),
            ("Pages/Detail/UI/Detail.swift", "DetailViewModel"),
            ("Pages/Detail/UI/Detail.swift", "NoticeRepository"),
            ("Pages/Detail/UI/Detail.swift", "DateValue"),
            ("App/Providers/Dependencies.swift", "NoticeRepository"),
        ]
        for (path, name) in allowed {
            #expect(FSDBoundaries.check(sources: declarations.merging([path: "struct Consumer { let value: \(name) }"]) { _, new in new }, exports: exports).isEmpty)
        }
        let forbidden: [(String, String, String)] = [
            ("Entities/Notice/Model/Bad.swift", "Save", "fsd-upward"),
            ("Entities/Notice/Model/Bad.swift", "OrganizationModel", "fsd-cross-slice"),
            ("Widgets/Card/UI/Bad.swift", "SiblingViewModel", "fsd-cross-slice"),
            ("Pages/Detail/UI/Bad.swift", "NoticeRecord", "fsd-public-api"),
            ("Entities/Notice/UI/Bad.swift", "NoticeRepository", "pure-ui-effect"),
            ("Widgets/Card/UI/CardContent.swift", "CardViewModel", "pure-ui-effect"),
            ("Widgets/Card/Model/Bad.swift", "NoticeStorageCodec", "fsd-public-api"),
            ("Shared/UI/Bad.swift", "UserDefaults", "pure-ui-effect"),
            ("Entities/Notice/UI/Bad.swift", "URLSession", "pure-ui-effect"),
        ]
        for (path, name, rule) in forbidden {
            let errors = FSDBoundaries.check(sources: declarations.merging([path: "struct Consumer { let value: \(name) }"]) { _, new in new }, exports: exports)
            #expect(errors.contains { $0.rule == rule }, "\(path) → \(name): \(errors)")
        }
        #expect(FSDBoundaries.check(sources: ["Shared/UI/Label.swift": "struct Label { let text = \"UserDefaults NoticeModel\" } // URLSession"], exports: [:]).isEmpty)
        #expect(FSDBoundaries.check(sources: ["App/Bad.swift": "struct Bad {}"], exports: [:]).contains { $0.rule == "fsd-path" })
        #expect(FSDBoundaries.check(sources: ["Widgets/Notice/Card/UI/Bad.swift": "struct Bad {}"], exports: [:]).contains { $0.rule == "fsd-path" })
        #expect(FSDBoundaries.check(sources: ["Shared/Lib/Bad.swift": "struct {"], exports: [:]).contains { $0.rule == "swift-syntax" })
    }
}
