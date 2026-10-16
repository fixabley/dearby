import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

/// Evidence files for one saved history: photos from the library or a PDF, up to three, 5MB each (contract "인증자료").
struct EvidenceSection: View {
    let historyID: String
    let items: [EvidenceMeta]
    let state: ProfileModel
    let onChange: ([EvidenceMeta]) -> Void
    @State private var photo: PhotosPickerItem?
    @State private var importing = false
    @State private var busy = false
    @State private var error: String?
    private static let limit = 3
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(items) { item in
                HStack(spacing: 10) {
                    Image(systemName: item.contentType == "application/pdf" ? "doc.richtext" : "photo").foregroundStyle(DearbyStyle.teal).frame(width: 24)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.fileName).font(.dearby(.subheadline)).lineLimit(1)
                        Text("\(item.size / 1024)KB").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                    }
                    Spacer()
                    Button { remove(item) } label: {
                        Image(systemName: "minus.circle").foregroundStyle(DearbyStyle.quiet).frame(width: 44, height: 44)
                    }.accessibilityLabel("\(item.fileName) 삭제").disabled(busy)
                }
            }
            if items.count < Self.limit {
                HStack(spacing: 10) {
                    PhotosPicker(selection: $photo, matching: .images) {
                        Label("사진", systemImage: "photo.on.rectangle").frame(maxWidth: .infinity, minHeight: 44)
                    }.buttonStyle(DearbyButtonStyle(outlined: true)).disabled(busy)
                    Button { importing = true } label: { Label("PDF", systemImage: "doc").frame(maxWidth: .infinity, minHeight: 44) }
                        .buttonStyle(DearbyButtonStyle(outlined: true)).disabled(busy)
                }
            } else {
                Text("인증자료는 \(Self.limit)개까지 올릴 수 있어요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
            }
            if busy { ProgressView().tint(DearbyStyle.teal) }
            if let error { DearbyFieldError(text: error) }
        }
        .onChange(of: photo) { _, item in
            guard let item else { return }
            photo = nil
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self) else { error = "사진을 읽지 못했어요."; return }
                let type = item.supportedContentTypes.first { ["image/jpeg", "image/png", "image/webp"].contains($0.preferredMIMEType ?? "") }
                await upload(data: data, fileName: "photo." + (type?.preferredFilenameExtension ?? "jpg"), contentType: type?.preferredMIMEType ?? "image/jpeg")
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.pdf]) { result in
            guard case .success(let url) = result else { return }
            Task {
                guard url.startAccessingSecurityScopedResource() else { error = "파일을 열지 못했어요."; return }
                defer { url.stopAccessingSecurityScopedResource() }
                guard let data = try? Data(contentsOf: url) else { error = "파일을 읽지 못했어요."; return }
                await upload(data: data, fileName: url.lastPathComponent, contentType: "application/pdf")
            }
        }
    }
    private func upload(data: Data, fileName: String, contentType: String) async {
        guard data.count <= 5 * 1024 * 1024 else { error = "파일은 5MB까지 올릴 수 있어요."; return }
        busy = true
        error = await state.upload(historyID: historyID, fileName: fileName, contentType: contentType, data: data)
        busy = false
        refresh()
    }
    private func remove(_ item: EvidenceMeta) {
        busy = true
        Task {
            error = await state.deleteEvidence(historyID: historyID, evidenceID: item.id)
            busy = false
            refresh()
        }
    }
    private func refresh() {
        if let history = state.profile?.histories.first(where: { $0.id == historyID }) { onChange(history.evidence) }
    }
}
