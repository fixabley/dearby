import Foundation
import SwiftData

@main
struct SwiftDataOrganizationTests {
    @MainActor static func main() throws {
        let folder = URL(fileURLWithPath: "apps/ios/build/organization-disk-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let configuration = ModelConfiguration(url: folder.appendingPathComponent("store"), cloudKitDatabase: .none)
        do {
            let container = try ModelContainer(for: OrganizationRecord.self, configurations: configuration)
            let context = ModelContext(container); context.autosaveEnabled = false
            context.insert(OrganizationRecord(OrganizationModel(id: "parent", name: "학교", parentId: nil)))
            context.insert(OrganizationRecord(OrganizationModel(id: "child", name: "기관", parentId: "parent")))
            try context.save()
        }
        // Fresh container/context, no snapshot reinsertion.
        let reopened = try ModelContainer(for: OrganizationRecord.self, configurations: configuration)
        let context = ModelContext(reopened); context.autosaveEnabled = false
        let source = CountingDiskSource(SwiftDataOrganizationSource(context: context, external: SnapshotOrganizationSource(organizations: [])))
        let repository = OrganizationRepository(source: source)
        precondition(source.calls == 0)
        let path = try repository.path(to: "child")
        precondition(path.map(\.name) == ["학교", "기관"] && source.calls == 2)
        _ = try repository.path(to: "child")
        precondition(source.calls == 2)
        let missing = try repository.organization("unknown")
        precondition(missing == nil && source.calls == 3)
        let external = CountingExternal()
        let promoted = OrganizationRepository(source: SwiftDataOrganizationSource(context: context, external: external))
        let value = try promoted.organization("external")
        precondition(value?.name == "외부 기관" && external.calls == 1)
        _ = try promoted.organization("external")
        precondition(external.calls == 1)
        let fresh = OrganizationRepository(source: SwiftDataOrganizationSource(context: ModelContext(reopened), external: external))
        _ = try fresh.organization("external")
        precondition(external.calls == 1, "L2 hit bypasses external mock")
        let failing = OrganizationRepository(source: SwiftDataOrganizationSource(context: context, external: external, commit: { _ in throw TestFailure.storage }))
        do { _ = try failing.organization("failed-save"); preconditionFailure() } catch TestFailure.storage {}
        let diskOnly = SwiftDataOrganizationSource(context: context, external: SnapshotOrganizationSource(organizations: []))
        let rolledBack = try diskOnly.fetch(id: "failed-save")
        precondition(rolledBack == nil)
        do { _ = try failing.organization("failed-save"); preconditionFailure() } catch TestFailure.storage {}
        precondition(external.calls == 3, "Failed save never populates L1")
        repository.replaceSource(FailingOrganizationSource())
        do { _ = try repository.organization("child"); preconditionFailure("Failure must not become missing") }
        catch TestFailure.storage {}
        print("PASS: SwiftData organization disk reopen, lazy ID fetch/L1 hit, missing vs failure")
    }
}
private enum TestFailure: Error { case storage }
@MainActor private final class CountingDiskSource: OrganizationSource {
    let disk: SwiftDataOrganizationSource
    var calls = 0
    init(_ disk: SwiftDataOrganizationSource) { self.disk = disk }
    func fetch(id: String) throws -> OrganizationModel? { calls += 1; return try disk.fetch(id: id) }
}
@MainActor private struct FailingOrganizationSource: OrganizationSource {
    func fetch(id: String) throws -> OrganizationModel? { throw TestFailure.storage }
}

@MainActor private final class CountingExternal: OrganizationSource {
    var calls = 0
    func fetch(id: String) -> OrganizationModel? {
        calls += 1
        return OrganizationModel(id: id, name: "외부 기관", parentId: nil)
    }
}
