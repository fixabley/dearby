import SwiftUI
import UIKit

/// 사진 위치·크기 조절 (contract "프로필 사진" 2026-10-26): the picked photo under a fixed circle; pinch to zoom, drag to move. The image always
/// covers the circle. `선택` hands back the square JPEG under it (`PhotoCrop`); the photo shows round everywhere.
struct PhotoCropView: View {
    let image: UIImage
    let cancel: () -> Void
    let done: (Data) -> Void
    @State private var zoom: CGFloat = 1
    @State private var zoomStart: CGFloat?
    @State private var offset = CGSize.zero
    @State private var offsetStart: CGSize?
    @State private var diameter: CGFloat = 1
    private static let maxZoom: CGFloat = 4
    var body: some View {
        GeometryReader { proxy in
            let side = max(1, min(proxy.size.width, proxy.size.height) - 32)
            let scale = side / min(image.size.width, image.size.height) * zoom
            ZStack {
                Image(uiImage: image).resizable().frame(width: image.size.width * scale, height: image.size.height * scale).offset(offset)
                Path { path in
                    path.addRect(CGRect(origin: .zero, size: proxy.size))
                    path.addEllipse(in: CGRect(x: (proxy.size.width - side) / 2, y: (proxy.size.height - side) / 2, width: side, height: side))
                }.fill(.black.opacity(0.6), style: FillStyle(eoFill: true)).allowsHitTesting(false)
                Circle().stroke(.white.opacity(0.8), lineWidth: 1).frame(width: side, height: side).allowsHitTesting(false)
            }
            .frame(width: proxy.size.width, height: proxy.size.height).clipped().contentShape(Rectangle())
            .gesture(drag.simultaneously(with: pinch))
            .onAppear { diameter = side }
            .onChange(of: side) { _, new in diameter = new; offset = clamped(offset) }
        }
        .background(.black)
        .accessibilityElement(children: .ignore).accessibilityLabel("고른 사진")
        .accessibilityValue("\(Int(zoom * 100))퍼센트 확대")
        .accessibilityHint("위아래로 밀어 크기를 바꿔요")
        .accessibilityAdjustableAction { direction in
            zoom = min(Self.maxZoom, max(1, zoom + (direction == .increment ? 0.25 : -0.25)))
            offset = clamped(offset)
        }
        .safeAreaInset(edge: .bottom) {
            Text("두 손가락으로 크기를, 끌어서 위치를 맞춰요").font(.dearby(.footnote)).foregroundStyle(.white.opacity(0.8))
                .frame(maxWidth: .infinity).padding(.vertical, 12)
        }
        .navigationTitle("사진 위치 조절").navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.black, for: .navigationBar).toolbarColorScheme(.dark, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("취소", action: cancel) }
            ToolbarItem(placement: .confirmationAction) {
                Button("선택") {
                    let square = PhotoCrop.square(imageSize: image.size, diameter: diameter, zoom: zoom, offset: offset)
                    if let jpeg = PhotoCrop.jpeg(image, square: square) { done(jpeg) }
                }.fontWeight(.semibold)
            }
        }
    }
    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let start = offsetStart ?? offset
                offsetStart = start
                offset = clamped(CGSize(width: start.width + value.translation.width, height: start.height + value.translation.height))
            }
            .onEnded { _ in offsetStart = nil }
    }
    private var pinch: some Gesture {
        MagnifyGesture()
            .onChanged { value in
                let start = zoomStart ?? zoom
                zoomStart = start
                zoom = min(Self.maxZoom, max(1, start * value.magnification))
                offset = clamped(offset)
            }
            .onEnded { _ in zoomStart = nil }
    }
    private func clamped(_ value: CGSize) -> CGSize {
        let limit = PhotoCrop.limit(imageSize: image.size, diameter: diameter, zoom: zoom)
        return CGSize(width: min(limit.width, max(-limit.width, value.width)), height: min(limit.height, max(-limit.height, value.height)))
    }
}
