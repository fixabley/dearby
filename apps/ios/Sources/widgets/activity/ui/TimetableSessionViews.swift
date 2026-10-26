import SwiftUI

/// The track badge (contract "트랙·세션 시간표" 2026-10-26): the track's palette color as an 18% fill with a dot and primary text (contrast in
/// light and dark), the room after the name. `large` for 내 시간표, where the room says where to go.
struct TimetableTrackBadge: View {
    let track: TimetableTrack
    var large = false
    var body: some View {
        HStack(spacing: 5) {
            Circle().fill(DearbyStyle.palette(track.position)).frame(width: 7, height: 7)
            Text([track.name, track.room].compactMap { $0 }.joined(separator: " · ")).lineLimit(large ? 2 : 1)
        }
        .font(.dearby(large ? .subheadline : .caption).weight(.semibold)).foregroundStyle(.primary)
        .padding(.horizontal, 8).padding(.vertical, 4)
        .background(DearbyStyle.palette(track.position).opacity(0.18), in: Capsule())
    }
}

enum TimetableText {
    static func range(_ session: TimetableSession, zone: String) -> String {
        ActivityText.time(session.start, zone: zone) + " – " + ActivityText.time(session.end, zone: zone)
    }
    /// 쉬는시간·점심 bands say what they are when the title does not.
    static func breakLabel(_ kind: TimetableSession.Kind) -> String { kind == .meal ? "점심" : "쉬는시간" }
    /// What a card reads as: time, track, title, speakers (contract: 접근성 라벨 시간·트랙·제목).
    static func spoken(_ session: TimetableSession, track: TimetableTrack?, zone: String) -> String {
        [ActivityText.time(session.start, zone: zone) + "부터 " + ActivityText.time(session.end, zone: zone) + "까지",
         track.map { [$0.name, $0.room].compactMap { $0 }.joined(separator: " ") } ?? (session.trackID == nil ? "전체 트랙" : nil),
         session.title, session.speakers.isEmpty ? nil : "연사 " + session.speakers.map(\.text).joined(separator: ", ")]
            .compactMap { $0 }.joined(separator: ", ")
    }
}

/// One session in the table or 내 시간표: track badge, up to two 분야 tags, title, speakers and the heart (`heart`/`heart.fill`, `찜`/`찜 해제`).
/// 쉬는시간·점심 are a quiet band without a heart. The card opens the session sheet; the heart sits outside the card's button.
struct TimetableSessionCard: View {
    let session: TimetableSession
    let track: TimetableTrack?
    let zone: String
    /// Show the time inside the card (내 시간표 and the single-track list have no time column).
    var showsTime = false
    var largeTrack = false
    /// `같은 시간 찜 n개` when the favorite overlaps others (contract "찜 겹침").
    var overlapCount = 0
    let open: () -> Void
    @Environment(SessionFavoritesModel.self) private var favorites
    var body: some View {
        if session.isBreak { band } else { card }
    }
    private var band: some View {
        HStack(spacing: 8) {
            Image(systemName: session.kind == .meal ? "fork.knife" : "cup.and.saucer")
            Text(session.title.isEmpty ? TimetableText.breakLabel(session.kind) : session.title).lineLimit(2)
            Spacer(minLength: 0)
            if showsTime { Text(TimetableText.range(session, zone: zone)).monospacedDigit() }
        }
        .font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(.secondary)
        .padding(.horizontal, 12).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading).frame(minHeight: 44)
        .background(Color(.tertiarySystemFill), in: .rect(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .ignore).accessibilityLabel(TimetableText.spoken(session, track: track, zone: zone))
    }
    private var card: some View {
        let favorited = favorites.ids.contains(session.id)
        return Button(action: open) {
            VStack(alignment: .leading, spacing: 6) {
                if showsTime {
                    Text(TimetableText.range(session, zone: zone)).font(.dearby(.footnote).weight(.semibold)).monospacedDigit().foregroundStyle(.secondary)
                }
                if let track {
                    TimetableTrackBadge(track: track, large: largeTrack)
                } else if session.kind == .keynote {
                    Text("키노트").font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                        .padding(.horizontal, 8).padding(.vertical, 4).background(DearbyStyle.mint, in: Capsule())
                }
                if !session.tags.isEmpty {
                    HStack(spacing: 4) {
                        ForEach(session.tags.prefix(2)) { DearbyTagChip(title: $0.name, nameEn: $0.nameEn, accent: true) }
                    }.fixedSize(horizontal: false, vertical: true)
                }
                Text(session.title).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(.primary)
                    .multilineTextAlignment(.leading).lineLimit(3)
                if !session.speakers.isEmpty {
                    Text(session.speakers.map(\.text).joined(separator: ", ")).font(.dearby(.footnote)).foregroundStyle(.secondary).lineLimit(2)
                }
                if overlapCount > 1 {
                    Label("같은 시간 찜 \(overlapCount)개", systemImage: "exclamationmark.triangle.fill")
                        .font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.danger)
                }
                Spacer(minLength: 0)
            }
            .padding(EdgeInsets(top: 10, leading: 10, bottom: 10, trailing: 40))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(TimetableText.spoken(session, track: track, zone: zone) + (overlapCount > 1 ? ", 같은 시간 찜 \(overlapCount)개" : ""))
        .accessibilityHint("누르면 세션 설명을 보여요")
        .overlay(alignment: .topTrailing) { heart(favorited) }
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 12, style: .continuous))
        .overlay(alignment: .leading) {
            if let track {
                UnevenRoundedRectangle(topLeadingRadius: 12, bottomLeadingRadius: 12, style: .continuous)
                    .fill(DearbyStyle.palette(track.position)).frame(width: 4).accessibilityHidden(true)
            }
        }
    }
    private func heart(_ favorited: Bool) -> some View {
        Button { favorites.toggle(session.id) } label: {
            Image(systemName: favorited ? "heart.fill" : "heart").foregroundStyle(favorited ? Color.pink : Color.secondary)
                .symbolEffect(.bounce, value: favorited).font(.dearby(.body))
                .frame(width: 40, height: 40).contentShape(Rectangle())
        }
        .buttonStyle(.borderless).accessibilityLabel(favorited ? "찜 해제" : "찜")
        .accessibilityValue(session.title)
    }
}

/// A session's description and speakers (contract "트랙·세션 시간표": 세션을 누르면 설명·연사 상세), with the heart.
struct TimetableSessionSheet: View {
    let session: TimetableSession
    let track: TimetableTrack?
    let zone: String
    @Environment(SessionFavoritesModel.self) private var favorites
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        // A heart pressed signed out opens sign-in over this sheet.
        content.promptLayer()
    }
    @ViewBuilder private var content: some View {
        let favorited = favorites.ids.contains(session.id)
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(TimetableText.range(session, zone: zone)).font(.dearby(.subheadline).weight(.semibold)).monospacedDigit()
                            .foregroundStyle(.secondary)
                        if let track { TimetableTrackBadge(track: track, large: true) }
                        Text(session.title).font(.dearby(.title3).weight(.bold))
                        if !session.tags.isEmpty {
                            ChipFlow(spacing: 6) { ForEach(session.tags) { DearbyTagChip(title: $0.name, nameEn: $0.nameEn, accent: true) } }
                                .accessibilityElement(children: .ignore).accessibilityLabel("분야 " + session.tags.map(\.name).joined(separator: ", "))
                        }
                    }.padding(.vertical, 4)
                }
                if !session.speakers.isEmpty {
                    Section("연사") {
                        ForEach(session.speakers.indices, id: \.self) { index in
                            let speaker = session.speakers[index]
                            VStack(alignment: .leading, spacing: 2) {
                                Text(speaker.name).font(.dearby(.body).weight(.semibold))
                                if let affiliation = speaker.affiliation { Text(affiliation).font(.dearby(.footnote)).foregroundStyle(.secondary) }
                            }.accessibilityElement(children: .combine)
                        }
                    }
                }
                if let description = session.description {
                    Section("소개") { Text(description).font(.dearby(.body)).lineSpacing(4) }
                }
                if !session.isBreak {
                    Section {
                        Button { favorites.toggle(session.id) } label: {
                            Label(favorited ? "찜 해제" : "찜하기", systemImage: favorited ? "heart.fill" : "heart")
                        }.tint(favorited ? .pink : DearbyStyle.teal)
                    }
                }
            }
            .navigationTitle("세션").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
        .tint(DearbyStyle.teal)
    }
}
