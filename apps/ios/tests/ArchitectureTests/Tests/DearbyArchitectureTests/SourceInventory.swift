import Foundation

/// Anchor to this test source, never cwd, git-common-dir, or Harmonize's recursive root finder.
enum SourceInventory {
    static var iosRoot: URL {
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<5 { url.deleteLastPathComponent() }
        return url.standardizedFileURL
    }

    enum InventoryError: Error { case invalidRoot(String), symbolicLink(String), emptySources, missingLayer(String), directoryName(String) }

    static func productionFiles(iosRoot: URL) throws -> [URL] {
        let manager = FileManager.default
        let root = iosRoot.appendingPathComponent("Sources", isDirectory: true).standardizedFileURL
        guard manager.fileExists(atPath: iosRoot.appendingPathComponent("ARCHITECTURE.md").path),
              manager.fileExists(atPath: root.appendingPathComponent("app/entrypoint/DearbyApp.swift").path) else {
            throw InventoryError.invalidRoot(iosRoot.path)
        }
        if root.resolvingSymlinksInPath() != root { throw InventoryError.symbolicLink(root.path) }
        var files: [URL] = []
        func visit(_ directory: URL, inAssets: Bool = false, atRoot: Bool = false) throws {
            for url in try manager.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.isDirectoryKey, .isSymbolicLinkKey]) {
                let values = try url.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
                // Fail closed rather than silently follow another worktree/source tree.
                if values.isSymbolicLink == true { throw InventoryError.symbolicLink(url.path) }
                if values.isDirectory == true {
                    let isAssetStructure = inAssets || (atRoot && url.lastPathComponent == "Assets.xcassets")
                    if !isAssetStructure && url.lastPathComponent.range(of: "^[a-z][A-Za-z0-9]*$", options: .regularExpression) == nil {
                        throw InventoryError.directoryName(url.path)
                    }
                    try visit(url, inAssets: isAssetStructure)
                } else if url.pathExtension == "swift" { files.append(url.standardizedFileURL) }
            }
        }
        try visit(root, atRoot: true)
        guard !files.isEmpty else { throw InventoryError.emptySources }
        for layer in ["app", "widgets", "features", "entities", "shared"] {
            let prefix = root.appendingPathComponent(layer).path + "/"
            guard files.contains(where: { $0.path.hasPrefix(prefix) }) else {
                throw InventoryError.missingLayer(layer)
            }
        }
        return files.sorted { $0.path < $1.path }
    }
}
