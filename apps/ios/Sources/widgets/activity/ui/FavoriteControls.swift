import SwiftUI

/// The heart beside an activity or a program (contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25): `heart`/`heart.fill`, `즐겨찾기`/`즐겨찾기 해제`;
/// once favorited the bell (`bell.fill`/`bell`/`bell.slash`, contract "프로그램·활동별 알림 수준") opens the level sheet. For an activity, `mine`
/// adds the `+` (contract "`+` 내 활동에 추가": `plus.circle`, added `checkmark.circle.fill`). Signed out the heart opens sign-in (the app does).
struct FavoriteControls: View {
    let target: FavoriteTarget
    /// The `+`: whether the activity is in 내 활동, and what the tap does (add, or open it there).
    var mine: (added: Bool, action: () -> Void)?
    /// The favorites screen names the level beside the bell.
    var showsLevel = false
    @Environment(FavoritesModel.self) private var favorites
    @State private var choosingLevel = false
    var body: some View {
        let level = favorites.levels[target]
        HStack(spacing: 0) {
            Button { favorites.toggle(target) } label: {
                Image(systemName: level != nil ? "heart.fill" : "heart").foregroundStyle(level != nil ? Color.pink : Color.secondary)
                    .symbolEffect(.bounce, value: level != nil)
                    .frame(width: 40, height: 40).contentShape(Rectangle())
            }
            .accessibilityLabel(level != nil ? "즐겨찾기 해제" : "즐겨찾기").accessibilityIdentifier("favorite-\(target)")
            if let level {
                Button { choosingLevel = true } label: {
                    HStack(spacing: 2) {
                        Image(systemName: level.symbol)
                        if showsLevel { Text(level.title).font(.dearby(.caption)) }
                    }
                    .foregroundStyle(level == .none ? Color.secondary : DearbyStyle.teal)
                    .frame(minWidth: 40, minHeight: 40).contentShape(Rectangle())
                }
                .accessibilityLabel("알림 \(level.title)").accessibilityHint("누르면 알림 수준을 골라요")
            }
            if let mine {
                Button(action: mine.action) {
                    Image(systemName: mine.added ? "checkmark.circle.fill" : "plus.circle")
                        .foregroundStyle(mine.added ? DearbyStyle.teal : Color.secondary)
                        .frame(width: 40, height: 40).contentShape(Rectangle())
                }
                .accessibilityLabel(mine.added ? "내 활동에 있음" : "내 활동에 추가").accessibilityIdentifier("add-to-mine")
            }
        }
        .font(.dearby(.title3)).buttonStyle(.borderless)
        .sheet(isPresented: $choosingLevel) {
            if let level { NotifyLevelSheet(level: level) { favorites.setLevel(target, $0) } }
        }
    }
}

/// 알림 수준 (contract "프로그램·활동별 알림 수준"): three choices in a short sheet, like a channel's bell; choosing closes it.
struct NotifyLevelSheet: View {
    let level: NotifyLevel
    let choose: (NotifyLevel) -> Void
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List(NotifyLevel.allCases) { option in
                Button {
                    choose(option)
                    dismiss()
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: option.symbol).foregroundStyle(DearbyStyle.teal).frame(width: 28)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(option.title).font(.dearby(.body)).foregroundStyle(.primary)
                            Text(option.detail).font(.dearby(.footnote)).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if option == level { Image(systemName: "checkmark").foregroundStyle(DearbyStyle.teal) }
                    }
                }
                .accessibilityAddTraits(option == level ? .isSelected : [])
            }
            .navigationTitle("알림").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
        .presentationDetents([.medium])
        .tint(DearbyStyle.teal)
    }
}
