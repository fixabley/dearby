import Testing

extension ArchitectureTestSuite {
    struct PresentationContractTests {
        @Test func sharedDesignExceptionIsExportedUIOnly() {
            for layer in ["pages", "widgets"] {
                let consumer = "\(layer)/card/ui/Card.swift"
                for target in ["shared/ui/Button.swift", "shared/ui/buttons/Button.swift"] {
                    let sources = [consumer: "struct Card { let value: Design }", target: "struct Design {}"]
                    #expect(FSDBoundaries.check(sources: sources, exports: ["shared": ["Design"]]).isEmpty)
                    #expect(FSDBoundaries.check(sources: sources, exports: [:]).contains { $0.rule == "fsd-public-api" })
                }
                for segment in ["api", "lib", "model"] {
                    let sources = [consumer: "struct Card { let value: Storage }", "shared/\(segment)/Storage.swift": "struct Storage {}"]
                    #expect(FSDBoundaries.check(sources: sources, exports: ["shared": ["Storage"]]).contains { $0.rule == "fsd-distant" })
                }
                #expect(FSDBoundaries.check(sources: [consumer: "import Shared\nstruct Card {}"], exports: [:])
                    .contains { $0.rule == "fsd-distant" })
            }
            let sources = ["pages/card/ui/Card.swift": "struct Card { let value: Design }",
                           "shared/ui/Design.swift": "struct Design { let storage: UserDefaults }"]
            #expect(FSDBoundaries.check(sources: sources, exports: ["shared": ["Design"]]).contains { $0.rule == "pure-ui-effect" })
        }

        @Test func detailRouteExceptionDoesNotOpenFeatureOrStorageAccess() {
            let preferences = "features/checkCalendarOverlap/model/CalendarPreferences.swift"
            let exports = ["features/checkCalendarOverlap": ["CalendarPreferences"]]
            for route in ["NoticeDestinationView", "OtherRoute"] {
                let sources = ["app/routes/\(route).swift": "struct Route { let value: CalendarPreferences }",
                               preferences: "class CalendarPreferences {}"]
                let errors = FSDBoundaries.check(sources: sources, exports: exports)
                #expect(errors.isEmpty == (route == "NoticeDestinationView"))
                #expect(FSDBoundaries.check(sources: sources, exports: [:]).contains { $0.rule == "fsd-public-api" })
            }
            for (target, name) in [("features/checkCalendarOverlap/api/Provider.swift", "Provider"),
                                   ("features/checkCalendarOverlap/model/BusyCalendarSession.swift", "BusyCalendarSession"),
                                   ("entities/notice/api/NoticeRepository.swift", "NoticeRepository")] {
                let owner = FSDBoundaries.File(path: target, text: "class \(name) {}")
                let sources = ["app/routes/NoticeDestinationView.swift": "struct Route { let value: \(name) }",
                               target: "class \(name) {}"]
                #expect(FSDBoundaries.check(sources: sources, exports: [owner.slice: [name]]).contains { $0.rule == "fsd-distant" })
            }
            for effect in ["UserDefaults", "URLSession", "UIApplication", "openURL", "ModelContext", "EKEventStore", "MKMapItem"] {
                #expect(FSDBoundaries.check(sources: ["app/routes/NoticeDestinationView.swift": "struct Route { let value = \(effect).self }"], exports: [:])
                    .contains { $0.rule == "route-effect" })
            }
        }

        @Test func pureUIContractSurvivesFileRenameAndRejectsStaleEntries() {
            for path in ["widgets/card/ui/CardContent.swift", "widgets/card/ui/Renamed.swift"] {
                let sources = [path: "struct CardContent { let value: UserDefaults }"]
                #expect(FSDBoundaries.check(sources: sources, exports: [:], pureUI: ["CardContent"])
                    .contains { $0.rule == "pure-ui-effect" })
            }
            let source = ["widgets/card/ui/Renamed.swift": "struct NewName {}"]
            #expect(FSDBoundaries.check(sources: source, exports: [:], pureUI: ["CardContent"])
                .contains { $0.rule == "pure-ui-contract" })
            #expect(FSDBoundaries.check(sources: source, exports: [:], pureUI: ["NewName", "NewName"])
                .contains { $0.rule == "pure-ui-contract" })
            #expect(FSDBoundaries.check(sources: ["widgets/card/model/Value.swift": "struct Value {}"], exports: [:], pureUI: ["Value"])
                .contains { $0.rule == "pure-ui-contract" })
            // A name alone is no purity contract; a connected View may be named Content.
            #expect(FSDBoundaries.check(sources: ["widgets/card/ui/ConnectedContent.swift": "struct ConnectedContent { let model: CardViewModel }",
                "widgets/card/model/CardViewModel.swift": "class CardViewModel {}"], exports: [:]).isEmpty)
            #expect(FSDBoundaries.check(sources: ["widgets/card/ui/Renamed.swift": "struct CardContent { let callback: () -> Void }"],
                exports: [:], pureUI: ["CardContent"]).isEmpty)
        }
    }
}
