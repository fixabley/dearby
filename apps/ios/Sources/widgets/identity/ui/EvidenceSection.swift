import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

/// Evidence files for one saved history as a form `Section`: photos from the library or a PDF, up to three, 5MB each
/// (contract "인증자료"). Files are rows (swipe left to delete), the add actions are rows, `hint` and errors are the footer.
struct EvidenceSection: View {
    let historyID: String
    let items: [EvidenceMeta]
    let state: ProfileModel
    let hint: String
    let onChange: ([EvidenceMeta]) -> Void
    @State private var photo: PhotosPickerItem?
    @State private var importing = false
    @State private var busy = false
    @State private var error: String?
    private static let limit = 3
    var body: some View {
        Section {
            ForEach(items) { item in
                Label {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.fileName).lineLimit(1)
                        Text("\(item.size / 1024)KB").font(.dearby(.footnote)).foregroundStyle(.secondary)
                    }
                } icon: {
                    Image(systemName: item.contentType == "application/pdf" ? "paperclip" : "photo").imageScale(.large).frame(width: 24).foregroundStyle(.secondary)
                }
                .swipeActions { Button("삭제", role: .destructive) { remove(item) }.accessibilityLabel("\(item.fileName) 삭제").disabled(busy) }
            }
            if items.count < Self.limit {
                // Add rows: the whole row is the control (contract "입력 영역 터치" 2026-10-20).
                PhotosPicker(selection: $photo, matching: .images) {
                    Label("사진 올리기", systemImage: "photo").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                }.disabled(busy)
                Button { importing = true } label: {
                    Label("PDF 올리기", systemImage: "paperclip").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                }.disabled(busy)
            }
            if busy { ProgressView().frame(maxWidth: .infinity) }
        } header: {
            Text("인증자료").font(.dearby(.subheadline))
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                Text(items.count < Self.limit ? hint : "인증자료는 \(Self.limit)개까지 올릴 수 있어요. " + hint)
                if let error { DearbyFieldError(text: error) }
            }.font(.dearby(.subheadline))
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
