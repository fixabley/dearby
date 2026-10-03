import SwiftUI

struct ProfilePage: View {
    @Bindable var state: IdentityViewModel
    @State private var editing = false
    var body: some View {
        Group {
            if state.signedIn { profile } else { guest }
        }.background(.white).navigationTitle("내 프로필").navigationBarTitleDisplayMode(.inline)
            .toolbar(state.signedIn ? .hidden : .visible, for: .navigationBar)
            .sheet(isPresented: $editing) { NavigationStack { ProfileEditor(state: state) } }
    }
    private var guest: some View {
        ScrollView {
            VStack(spacing: 24) {
                Image(systemName: "person.text.rectangle").font(.system(size: 92, weight: .light))
                    .foregroundStyle(DearbyStyle.teal).padding(.top, 110)
                Text("나를 소개하는 명함을 만들어보세요.").font(.title2.bold()).multilineTextAlignment(.center)
                Text("연락처와 활동 이력을 정리하고\n상황에 맞는 명함으로 공유할 수 있어요.")
                    .font(.subheadline).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
                Button("로그인하고 시작하기") { state.signedIn = true }.buttonStyle(DearbyButtonStyle()).padding(.top, 12)
                Text("예시 프로필로 전환해요. 실제 로그인이나 정보 전송은 없어요.")
                    .font(.caption).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
            }.padding(20)
        }
    }
    private var profile: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack { Text("내 프로필").font(.title.bold()); Spacer(); Button("편집") { editing = true } }
                IdentityHeading(name: state.profileName, job: state.job, introduction: state.introduction, isFullProfile: true)
                Label("저장한 정보는 명함에서 선택한 항목만 공개돼요.", systemImage: "info.circle")
                    .font(.caption).foregroundStyle(DearbyStyle.teal).padding(12).background(DearbyStyle.mint, in: RoundedRectangle(cornerRadius: 8))
                HStack {
                    Text("연락처").font(.title2.bold()); Spacer()
                    Button("추가") {
                        state.contacts.append(.init(id: "custom-\(state.contacts.count)", kind: "link", displayLabel: "웹사이트", value: "https://example.com"))
                        editing = true
                    }
                }
                VStack(spacing: 4) {
                    ForEach(state.contacts) { contact in
                        Button { editing = true } label: {
                            HStack(spacing: 10) {
                                ContactSymbol(contact: contact, size: 22).frame(width: 26).foregroundStyle(DearbyStyle.teal)
                                Text(contact.displayLabel).font(.caption).frame(width: 62, alignment: .leading)
                                Text(contact.value).font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
                                Image(systemName: "chevron.right").font(.caption).foregroundStyle(DearbyStyle.quiet)
                            }.foregroundStyle(.primary).padding(10).frame(minHeight: 44)
                                .background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 8))
                        }.buttonStyle(.plain)
                    }
                }
                HStack {
                    Text("활동 이력").font(.title2.bold()); Spacer()
                    Button("추가") {
                        state.histories.append(.init(id: "history-\(state.histories.count)", title: "새로운 활동", role: "참가자", startDate: "2026.10"))
                        editing = true
                    }
                }
                HistoryTimeline(histories: state.histories, compact: true)
                Button("예시 로그아웃") { state.signedIn = false }.font(.caption)
            }.padding(20)
        }
    }
}
private struct ProfileEditor: View {
    @Bindable var state: IdentityViewModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        Form {
            Section("기본 정보") {
                TextField("이름", text: $state.profileName)
                TextField("소개", text: $state.introduction)
                TextField("직무", text: $state.job)
            }
            Section("연락처") {
                ForEach($state.contacts) { $contact in TextField(contact.displayLabel, text: $contact.value) }
            }
            Section("활동 이력") {
                ForEach($state.histories) { $history in
                    TextField("활동명", text: $history.title)
                    TextField("역할", text: $history.role)
                }
            }
            Text("변경 내용은 이번 실행에서만 유지돼요.").font(.caption)
        }.navigationTitle("프로필 편집").toolbar { Button("완료") { dismiss() } }
    }
}
