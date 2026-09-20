import SwiftUI

struct NoticeParticipationView: View {
    let audience: String
    var condition: String?

    var body: some View {
        InformationRow(title: "참여 대상", value: audience)
        if let condition { InformationRow(title: "참여 조건", value: condition) }
    }
}
