import SwiftUI

/// 명함 제작에서 공개 여부를 고르는 연락처 한 줄의 값.
struct CardComposerContact: Identifiable, Equatable {
    let id: String
    let kind: String
    let label: String
    var value: String
    var isPublic: Bool
}

/// 명함 제작에서 공개 여부를 고르는 활동 이력 한 줄의 값.
struct CardComposerHistory: Identifiable, Equatable {
    let id: String
    let title: String
    let detail: String
    var isPublic: Bool
}

/// 이름·직함·소개·공개할 연락처와 이력을 한 화면에서 입력하고 바로 발행하는 명함 제작 화면의 몸통.
/// 로그인 요구·발행 요청·오류 처리는 화면이 맡고, 이 위젯은 값과 `publish`만 받는다.
struct CardComposer: View {
    @Binding var name: String
    @Binding var job: String
    @Binding var introduction: String
    @Binding var contacts: [CardComposerContact]
    @Binding var histories: [CardComposerHistory]
    var requiresLogin = false
    var publishing = false
    var errorMessage: String?
    /// 명함 이름. 주면 맨 위에 선택 입력 칸을 보이고, 비워 두면 화면이 '내 명함'을 쓴다.
    var cardTitle: Binding<String>?
    let publish: () -> Void
    private var nameMissing: Bool { name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack(spacing: 14) {
                    DearbyAvatar(name: nameMissing ? "?" : name, size: 56)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(nameMissing ? "이름" : name).font(.dearby(.headline))
                            .foregroundStyle(nameMissing ? DearbyStyle.quiet : DearbyStyle.ink)
                        Text(job.isEmpty ? "직함" : job).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
                    }
                }.accessibilityHidden(true)
                if let cardTitle {
                    section("명함") {
                        field("명함 이름 (선택)", text: cardTitle, prompt: "비워 두면 '내 명함'으로 저장해요")
                    }
                }
                section("기본 정보") {
                    field("이름", text: $name, prompt: "실명 또는 활동명")
                    field("직함", text: $job, prompt: "예: 서비스 기획 · 커뮤니티")
                    field("소개", text: $introduction, prompt: "처음 만난 사람에게 건넬 한두 문장", multiline: true)
                }
                if !contacts.isEmpty {
                    section("공개할 연락처") {
                        ForEach($contacts) { $contact in
                            HStack(spacing: 12) {
                                Image(systemName: ContactModel(id: contact.id, kind: contact.kind, displayLabel: contact.label, value: contact.value).symbol)
                                    .frame(width: 24).foregroundStyle(DearbyStyle.teal).accessibilityHidden(true)
                                DearbyInlineField(label: contact.label, text: $contact.value, editing: true, prompt: contact.label)
                                Toggle("\(contact.label) 공개", isOn: $contact.isPublic).labelsHidden().tint(DearbyStyle.teal)
                            }
                        }
                    }
                }
                if !histories.isEmpty {
                    section("공개할 활동 이력") {
                        ForEach($histories) { $history in
                            Toggle(isOn: $history.isPublic) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(history.title).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.ink)
                                    if !history.detail.isEmpty {
                                        Text(history.detail).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                                    }
                                }
                            }.tint(DearbyStyle.teal).frame(minHeight: 44)
                        }
                    }
                }
            }.padding(20)
        }
        .safeAreaInset(edge: .bottom) { publishBar }
    }
    private var publishBar: some View {
        VStack(spacing: 8) {
            if let errorMessage {
                Text(errorMessage).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink).frame(maxWidth: .infinity, alignment: .leading)
            } else if nameMissing {
                Text("이름을 입력하면 발행할 수 있어요.").font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
            }
            Button(action: publish) {
                if publishing { ProgressView().tint(.white) } else { Text(requiresLogin ? "로그인하고 명함 발행" : "명함 발행") }
            }.buttonStyle(DearbyButtonStyle()).disabled(nameMissing || publishing)
                .accessibilityLabel(publishing ? "명함 발행 중" : (requiresLogin ? "로그인하고 명함 발행" : "명함 발행"))
            if requiresLogin {
                Text("발행할 때만 이메일 인증번호로 로그인해요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
            }
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.white)
            .overlay(alignment: .top) { Rectangle().fill(DearbyStyle.line).frame(height: 1) }
    }
    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink).accessibilityAddTraits(.isHeader)
            content()
        }
    }
    private func field(_ label: String, text: Binding<String>, prompt: String, multiline: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet).accessibilityHidden(true)
            DearbyInlineField(label: label, text: text, editing: true, prompt: prompt, multiline: multiline)
        }
    }
}

#if DEBUG
/// 미리보기와 캡처 테스트가 함께 쓰는 예시.
struct CardComposerSample: View {
    @State var name = "김지민"
    @State var job = "서비스 기획 · 커뮤니티"
    @State var introduction = ""
    @State var contacts = [CardComposerContact(id: "email", kind: "email", label: "이메일", value: "jimin@example.invalid", isPublic: true),
                           CardComposerContact(id: "phone", kind: "phone", label: "전화", value: "", isPublic: false)]
    @State var histories = [CardComposerHistory(id: "camp", title: "Dearby 메이커 캠프", detail: "2025 · 운영진", isPublic: true)]
    @State var cardTitle = ""
    var requiresLogin = true
    var body: some View {
        CardComposer(name: $name, job: $job, introduction: $introduction, contacts: $contacts, histories: $histories,
                     requiresLogin: requiresLogin, cardTitle: $cardTitle) {}
            .background(.white)
    }
}

#Preview("명함 제작") { CardComposerSample() }
#Preview("이름 없음") { CardComposerSample(name: "") }
#endif
