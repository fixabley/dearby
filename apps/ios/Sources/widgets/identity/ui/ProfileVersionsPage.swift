import SwiftUI

/// 변경 이력: every saved profile version, newest first; a version can be viewed and restored as a new version.
struct ProfileVersionsPage: View {
    let state: ProfileModel
    @State private var items: [ProfileVersionSummary] = []
    @State private var nextCursor: Int?
    @State private var loading = true
    @State private var error: String?
    var body: some View {
        List {
            if let error { Text(error).foregroundStyle(DearbyStyle.danger) }
            // First page only (contract "내용 로딩은 스켈레톤" 2026-10-22); 더 보기 keeps the rows already shown.
            if loading && items.isEmpty && error == nil { DearbyRowsSkeleton(count: 4) }
            ForEach(items) { item in
                NavigationLink {
                    ProfileVersionPage(state: state, version: item.version)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("버전 \(item.version)").font(.dearby(.headline))
                        Text(Self.when(item.createdAt)).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                    }.frame(minHeight: 44)
                }
            }
            if nextCursor != nil {
                Button("더 보기") { Task { await load() } }.frame(maxWidth: .infinity, minHeight: 44)
            }
            if !loading && items.isEmpty && error == nil { Text("아직 저장한 버전이 없어요.").foregroundStyle(DearbyStyle.quiet) }
        }
        .listStyle(.plain).navigationTitle("변경 이력").navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }
    private func load() async {
        loading = true
        do {
            let page = try await state.versions(cursor: nextCursor)
            items += page.items
            nextCursor = page.nextCursor
        } catch { self.error = "변경 이력을 불러오지 못했어요." }
        loading = false
    }
    static func when(_ iso: String) -> String {
        guard let date = parse(iso) else { return iso }
        return date.formatted(.dateTime.year().month().day().hour().minute().locale(Locale(identifier: "ko_KR")))
    }
}

/// One version's content with "이 버전으로 되돌리기".
struct ProfileVersionPage: View {
    let state: ProfileModel
    let version: Int
    @State private var detail: ProfileVersionDetail?
    @State private var error: String?
    @State private var restoring = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let detail {
                    Text(ProfileVersionsPage.when(detail.createdAt)).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                    ProfileReadView(profile: detail.profile)
                } else if let error {
                    Text(error).foregroundStyle(DearbyStyle.danger)
                } else {
                    // Contract "내용 로딩은 스켈레톤" 2026-10-22.
                    ProfileReadView(profile: .placeholder).dearbySkeleton()
                }
            }.padding(20)
        }
        .safeAreaInset(edge: .bottom) {
            if detail != nil {
                VStack(spacing: 6) {
                    if let error { Text(error).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.danger) }
                    Button { restore() } label: {
                        Group { if restoring { ProgressView().tint(DearbyStyle.onTeal) } else { Text("이 버전으로 되돌리기") } }.frame(maxWidth: .infinity)
                    }.buttonStyle(.borderedProminent).controlSize(.large).tint(DearbyStyle.tealFill).font(.dearby(.body).weight(.semibold)).disabled(restoring)
                    Text("되돌리면 새 버전으로 저장되고 이력은 남아요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                }.padding(.horizontal, 20).padding(.vertical, 12).background(Color(.systemBackground))
            }
        }
        .navigationTitle("버전 \(version)").navigationBarTitleDisplayMode(.inline)
        .task { do { detail = try await state.version(version) } catch { self.error = "이 버전을 불러오지 못했어요." } }
    }
    private func restore() {
        restoring = true
        Task {
            error = await state.restore(version)
            restoring = false
            if error == nil { dismiss() }
        }
    }
}

extension AccountHistory {
    /// Timeline presentation: 조직 · 프로그램 · 활동 as the title, dates as `2026.03`.
    var historyModel: HistoryModel {
        let month = { (text: String) in String(text.prefix(7)).replacingOccurrences(of: "-", with: ".") }
        return HistoryModel(id: id, title: chain.joined(separator: " · "), role: roleTitle, startDate: month(startDate),
                            endDate: endDate.map(month) ?? "진행 중", verified: isVerified, description: description)
    }
}

extension ProfileVersionsPage {
    /// Server timestamps carry fractional seconds; `.withFractionalSeconds` alone rejects whole seconds, so try both.
    static func parse(_ iso: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: iso) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: iso)
    }
}
