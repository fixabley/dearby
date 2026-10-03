import SwiftUI

struct ActivityArtwork: View {
    let activityID: String
    private var name: String {
        switch activityID {
        case "conference": "ConferencePhoto"
        case "camp": "CampPhoto"
        default: "MeetupPhoto"
        }
    }
    var body: some View {
        Image(name).resizable().scaledToFill().accessibilityLabel("활동 소개용 예시 사진")
    }
}
