import SwiftUI

struct ActivityArtwork: View {
    let activityID: String
    // Example photos belong to example activities only; catalog activities have no images.
    private var name: String? {
        switch activityID {
        case "conference": "ConferencePhoto"
        case "camp": "CampPhoto"
        case "meetup": "MeetupPhoto"
        default: nil
        }
    }
    var body: some View {
        if let name {
            Image(name).resizable().scaledToFill().accessibilityLabel("활동 소개용 예시 사진")
        } else {
            ZStack {
                DearbyStyle.mint
                Image(systemName: "calendar").font(.dearby(.largeTitle)).foregroundStyle(DearbyStyle.teal)
            }.accessibilityHidden(true)
        }
    }
}
