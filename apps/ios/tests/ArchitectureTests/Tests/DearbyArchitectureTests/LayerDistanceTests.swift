import Testing

struct LayerDistanceTests {
    private let paths = ["app/routes/Consumer.swift", "pages/alpha/model/Consumer.swift",
                         "widgets/alpha/model/Consumer.swift", "features/alpha/model/Consumer.swift",
                         "entities/alpha/model/Consumer.swift", "shared/lib/Consumer.swift"]

    @Test func everyLayerPairAndImport() {
        for (sourceRank, path) in paths.enumerated() {
            for (targetRank, targetPath) in paths.enumerated() {
                let target = targetPath.replacingOccurrences(of: "Consumer.swift", with: "Target.swift")
                let file = FSDBoundaries.File(path: target, text: "struct Target {}")
                let sources = [path: "struct Consumer { let value: Target }", target: "struct Target {}"]
                let errors = FSDBoundaries.check(sources: sources, exports: [file.slice: ["Target"]])
                #expect(errors.contains { $0.rule == "fsd-distant" } == (targetRank > sourceRank + 2))
                #expect(errors.contains { $0.rule == "fsd-upward" } == (targetRank < sourceRank))
                #expect(errors.isEmpty == (targetRank >= sourceRank && targetRank <= sourceRank + 2))
                let imports = FSDBoundaries.check(sources: [path: "import \(FSDBoundaries.layers[targetRank])\nstruct Consumer {}"], exports: [:])
                #expect(imports.contains { $0.rule == "fsd-distant" } == (targetRank > sourceRank + 2))
                #expect(imports.contains { $0.rule == "fsd-upward" } == (targetRank < sourceRank))
            }
            #expect(FSDBoundaries.check(sources: [path: "import SwiftUI\nimport Foundation\nimport EventKit\nstruct Consumer {}"], exports: [:]).isEmpty)
        }
    }

    @Test func providerExceptionIsExactAndStillChecksPublicAPI() {
        let declaration = "entities/notice/model/NoticeModel.swift"
        for path in ["app/providers/Dependencies.swift", "app/routes/Dependencies.swift",
                     "app/entrypoint/Dependencies.swift", "app/routes/providers/Dependencies.swift",
                     "app/providersExtra/Dependencies.swift"] {
            let isProvider = path == "app/providers/Dependencies.swift"
            let sources = [declaration: "struct NoticeModel {}", path: "struct Consumer { let notice: NoticeModel }"]
            let errors = FSDBoundaries.check(sources: sources, exports: ["entities/notice": ["NoticeModel"]])
            #expect(errors.contains { $0.rule == "fsd-distant" } == !isProvider)
            #expect(FSDBoundaries.check(sources: sources, exports: [:]).contains { $0.rule == "fsd-public-api" })
            let imports = FSDBoundaries.check(sources: [path: "import struct Entities.NoticeModel\nstruct Consumer {}"], exports: [:])
            #expect(imports.contains { $0.rule == "fsd-distant" } == !isProvider)
        }
        for declaration in ["struct Hidden: View {}", "struct Hidden: ViewModifier {}"] {
            #expect(ArchitectureRules.check(path: "app/providers/Hidden.swift", text: declaration).contains { $0.rule == "provider-ui" })
        }
    }

    @Test func noAliasOrReexportFacadeAndNoSiblingWidgets() {
        #expect(FSDBoundaries.check(sources: ["features/sample/model/Alias.swift": "typealias Alias = Value",
            "shared/lib/Value.swift": "struct Value {}"], exports: ["shared": ["Value"]]).contains { $0.rule == "fsd-alias" })
        #expect(FSDBoundaries.check(sources: ["features/sample/model/Alias.swift": "@_exported import Shared"], exports: [:])
            .contains { $0.rule == "fsd-reexport" })
        #expect(FSDBoundaries.check(sources: ["widgets/card/ui/Card.swift": "struct Card { let other: Detail }",
            "widgets/detail/ui/Detail.swift": "struct Detail {}"], exports: ["widgets/detail": ["Detail"]])
            .contains { $0.rule == "fsd-cross-slice" })
    }
}
