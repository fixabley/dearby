import SwiftUI

/// Native date/time hierarchy with no event/model/OS dependency.
struct EventTimeRows: View {
    let lines: [EventPeriodPresentation.Line]
    let note: String?

    var body: some View {
        HStack(alignment: .top, spacing: NativeSpacing.related) {
            Image(systemName: "calendar").font(.caption).foregroundStyle(.secondary).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: NativeSpacing.related) {
                ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                    VStack(alignment: .leading, spacing: NativeSpacing.compact) {
                        if let label = line.label, !line.time.hasSuffix("부터"), !line.time.hasSuffix("까지") {
                            Text(label).font(.caption).foregroundStyle(.secondary)
                        }
                        Text(line.date).font(.body)
                        Text(line.time).font(.subheadline)
                    }
                    .accessibilityElement(children: .combine)
                }
                if let note { Text(note).font(.footnote).foregroundStyle(.secondary) }
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

#Preview("기간 · 날짜와 시간 · 큰 글자") {
    List {
        EventTimeRows(lines: [.init(label: nil, date: "2026년 9월 15일", time: "14:00–16:00")], note: nil)
        EventTimeRows(lines: [.init(label: "시작", date: "2026년 12월 31일", time: "시간 미확인"),
                             .init(label: "종료", date: "2027년 1월 1일", time: "16:00")], note: nil)
        LocationInformation(name: "긴 이름의 행사 장소", detail: "5층 세미나실\n상세 주소 확인 필요") {
            Button(action: {}) { Label("지도 보기", systemImage: "map").labelStyle(.iconOnly).frame(minWidth: 44, minHeight: 44) }
        }
    }.environment(\.dynamicTypeSize, .accessibility5)
}
