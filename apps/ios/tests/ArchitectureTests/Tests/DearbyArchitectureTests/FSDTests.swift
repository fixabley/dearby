import Foundation
import Testing

extension ArchitectureTestSuite {
    struct FSDTests {
        @Test func productionBoundaries() throws {
            let ios = SourceInventory.iosRoot
            let root = ios.appendingPathComponent("Dearby").path + "/"
            let exports = try JSONDecoder().decode([String: [String]].self,
                from: Data(contentsOf: ios.appendingPathComponent("architecture/public-api.json")))
            #expect(!FileManager.default.fileExists(atPath: ios.appendingPathComponent("architecture/migration-paths.json").path), "migration mapping must stay removed")
            let files = try SourceInventory.productionFiles(iosRoot: ios)
            var sources: [String: String] = [:]
            for file in files {
                let path = String(file.path.dropFirst(root.count))
                let canonical = path
                #expect(sources[canonical] == nil, "duplicate production path \(canonical)")
                sources[canonical] = try String(contentsOf: file, encoding: .utf8)
            }
            let errors = FSDBoundaries.check(sources: sources, exports: exports)
            #expect(errors.isEmpty, "\(errors.map(\.description).joined(separator: "\n"))")
        }

        @Test func manifestAndConditionalDeclarations() {
            #expect(FSDBoundaries.check(sources: ["": "struct Empty {}"], exports: [:]).contains { $0.rule == "fsd-path" })
            #expect(FSDBoundaries.check(sources: ["shared/lib/A.swift": "struct A {}"], exports: ["shared": ["Missing"]]).contains { $0.rule == "fsd-manifest" })
            #expect(FSDBoundaries.check(sources: ["shared/lib/A.swift": "struct A {}", "shared/lib/B.swift": "struct A {}"], exports: [:]).contains { $0.rule == "fsd-ambiguous" })
            for declaration in ["actor Hidden {}", "typealias Hidden = Int", "func Hidden() {}"] {
                let sources = ["entities/notice/api/Hidden.swift": "#if os(iOS)\n" + declaration + "\n#endif",
                               "pages/detail/model/Consumer.swift": "struct Consumer { let value = Hidden() }"]
                #expect(FSDBoundaries.check(sources: sources, exports: [:]).contains { $0.rule == "fsd-public-api" })
            }
        }

        @Test func dependencyFixtures() {
            let declarations = [
                "entities/notice/model/NoticeModel.swift": "struct NoticeModel {}",
                "entities/notice/api/NoticeRepository.swift": "class NoticeRepository {}",
                "entities/notice/api/NoticeRecord.swift": "struct NoticeRecord {}",
                "entities/notice/api/NoticeStorageCodec.swift": "enum NoticeStorageCodec {}",
                "entities/organization/model/OrganizationModel.swift": "struct OrganizationModel {}",
                "widgets/card/model/CardViewModel.swift": "class CardViewModel {}",
                "widgets/sibling/model/SiblingViewModel.swift": "class SiblingViewModel {}",
                "features/save/model/Save.swift": "class Save {}",
                "features/checkCalendarOverlap/model/CalendarConnectionState.swift": "enum CalendarConnectionState {}",
                "pages/detail/model/DetailViewModel.swift": "class DetailViewModel {}",
                "shared/lib/DateValue.swift": "struct DateValue {}",
            ]
            let exports = ["entities/notice": ["NoticeModel", "NoticeRepository"],
                           "entities/organization": ["OrganizationModel"], "widgets/card": ["CardViewModel"],
                           "widgets/sibling": ["SiblingViewModel"], "features/save": ["Save"],
                           "pages/detail": ["DetailViewModel"], "shared": ["DateValue"],
                           "features/checkCalendarOverlap": ["CalendarConnectionState"]]
            let allowed: [(String, String)] = [
                ("features/checkCalendarOverlap/ui/Control.swift", "CalendarConnectionState"),
                ("entities/notice/ui/Label.swift", "NoticeModel"),
                ("widgets/card/ui/Card.swift", "CardViewModel"),
                ("widgets/card/ui/CardContent.swift", "NoticeModel"),
                ("widgets/card/ui/Card.swift", "Save"),
                ("pages/detail/ui/Detail.swift", "DetailViewModel"),
                ("app/providers/Dependencies.swift", "NoticeRepository"),
            ]
            for (path, name) in allowed {
                #expect(FSDBoundaries.check(sources: declarations.merging([path: "struct Consumer { let value: \(name) }"]) { _, new in new }, exports: exports).isEmpty)
            }
            let forbidden: [(String, String, String)] = [
                ("pages/detail/ui/Detail.swift", "NoticeRepository", "fsd-distant"),
                ("pages/detail/ui/Detail.swift", "DateValue", "fsd-distant"),
                ("shared/ui/Control.swift", "CalendarConnectionState", "fsd-upward"),
                ("entities/notice/model/Bad.swift", "Save", "fsd-upward"),
                ("entities/notice/model/Bad.swift", "OrganizationModel", "fsd-cross-slice"),
                ("widgets/card/ui/Bad.swift", "SiblingViewModel", "fsd-cross-slice"),
                ("pages/detail/ui/Bad.swift", "NoticeRecord", "fsd-public-api"),
                ("entities/notice/ui/Bad.swift", "NoticeRepository", "pure-ui-effect"),
                ("widgets/card/ui/CardContent.swift", "CardViewModel", "pure-ui-effect"),
                ("widgets/card/ui/Bad.swift", "NoticeStorageCodec", "fsd-public-api"),
                ("shared/ui/Bad.swift", "UserDefaults", "pure-ui-effect"),
                ("entities/notice/ui/Bad.swift", "URLSession", "pure-ui-effect"),
            ]
            for (path, name, rule) in forbidden {
                let errors = FSDBoundaries.check(sources: declarations.merging([path: "struct Consumer { let value: \(name) }"]) { _, new in new }, exports: exports)
                #expect(errors.contains { $0.rule == rule }, "\(path) → \(name): \(errors)")
            }
            #expect(FSDBoundaries.check(sources: ["shared/ui/Label.swift": "struct Label { let text = \"UserDefaults NoticeModel\" } // URLSession"], exports: [:]).isEmpty)
            #expect(FSDBoundaries.check(sources: ["app/Bad.swift": "struct Bad {}"], exports: [:]).contains { $0.rule == "fsd-path" })
            #expect(FSDBoundaries.check(sources: ["widgets/notice/card/ui/Bad.swift": "struct Bad {}"], exports: [:]).contains { $0.rule == "fsd-path" })
            #expect(FSDBoundaries.check(sources: ["shared/lib/Bad.swift": "struct {"], exports: [:]).contains { $0.rule == "swift-syntax" })
        }
    }
}
