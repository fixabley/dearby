package io.wid.dearby.domain.discovery

import io.wid.dearby.domain.ActivityCategory

// 앱에 내장된 "아이콘 통일" 표(native-v1.md)의 키 목록(admin-v1.md "발견 필터 구성" 아이콘 선택기, GET /v1/admin/icons).
// 키는 서버가 정한다: 활동 종류는 종류 코드 소문자, 분야는 태그 nameEn, 나머지는 뜻의 영문 camelCase. 표에 없는 아이콘은 고를 수 없다(새 아이콘은 코드 변경)
data class Icon(val key: String, val label: String, val ios: String, val android: String)

object Icons {
    const val DEFAULT_CATEGORY = "category" // 앱이 모르는 키를 받았을 때의 기본(종류 기타)
    const val DEFAULT_TAG = "tag" // 표에 없는 분야 태그의 기본

    val ALL: List<Icon> = listOf(
        // 활동 종류(종류 코드 소문자)
        Icon("bootcamp", "부트캠프", "graduationcap", "School"),
        Icon("conference", "컨퍼런스", "person.3", "Groups"),
        Icon("workshop", "워크샵", "pencil.and.ruler", "DesignServices"),
        Icon("study", "스터디", "books.vertical", "LibraryBooks"),
        Icon("club", "동아리", "person.2", "Group"),
        Icon("lecture", "강연", "mic", "Mic"),
        Icon("hackathon", "해커톤", "keyboard", "Keyboard"),
        Icon("contest", "공모전", "trophy", "EmojiEvents"),
        Icon("meetup", "밋업", "cup.and.saucer", "LocalCafe"),
        Icon(DEFAULT_CATEGORY, "기타(종류)", "square.grid.2x2", "Category"),
        Icon("timetable", "시간표", "tablecells", "ViewTimeline"),
        // 분야 태그(nameEn)
        Icon("startup", "창업", "lightbulb", "Lightbulb"),
        Icon("project", "프로젝트", "hammer", "Build"),
        Icon("mentoring", "멘토링", "bubble.left.and.bubble.right", "Forum"),
        Icon("networking", "네트워킹", "point.3.connected.trianglepath.dotted", "Hub"),
        Icon("funding", "장학·지원금", "wonsign.circle", "Payments"),
        Icon("it", "IT", "laptopcomputer", "Computer"),
        Icon("dataAi", "데이터·AI", "sparkles", "AutoAwesome"),
        Icon("cloud", "클라우드", "cloud", "Cloud"),
        Icon("semiconductor", "반도체", "cpu", "Memory"),
        Icon("mechanical", "기계", "gearshape.2", "PrecisionManufacturing"),
        Icon("civilEngineering", "토목·건설", "building.2", "Domain"),
        Icon("security", "보안", "lock.shield", "Security"),
        Icon("business", "경제·경영", "chart.bar", "BarChart"),
        Icon("marketing", "마케팅·광고", "megaphone", "Campaign"),
        Icon("investing", "금융·투자", "chart.line.uptrend.xyaxis", "TrendingUp"),
        Icon("volunteering", "봉사", "heart", "VolunteerActivism"),
        Icon("reading", "독서·토론", "book", "MenuBook"),
        Icon("video", "영상", "video", "Videocam"),
        Icon("engineering", "공학", "wrench.and.screwdriver", "Engineering"),
        Icon("webApp", "웹·앱", "apps.iphone", "Devices"),
        Icon("game", "게임", "gamecontroller", "SportsEsports"),
        Icon("openSource", "오픈소스", "chevron.left.forwardslash.chevron.right", "Code"),
        Icon("backend", "백엔드", "server.rack", "Dns"),
        Icon(DEFAULT_TAG, "태그(기본)", "tag", "Sell"),
        // 의미별 고정 이름
        Icon("calendar", "일정·달력", "calendar", "Event"),
        Icon("clock", "시각", "clock", "Schedule"),
        Icon("place", "장소", "mappin.and.ellipse", "Place"),
        Icon("externalLink", "외부 링크", "arrow.up.right", "AutoMirrored.Rounded.OpenInNew"),
        Icon("edit", "수정", "pencil", "Edit"),
        Icon("delete", "삭제", "trash", "Delete"),
        Icon("add", "추가", "plus", "Add"),
        Icon("remove", "제거", "minus.circle", "RemoveCircle"),
        Icon("search", "검색", "magnifyingglass", "Search"),
        Icon("filter", "필터", "line.3.horizontal.decrease.circle", "FilterList"),
        Icon("clear", "지우기", "xmark.circle.fill", "Cancel"),
        Icon("close", "닫기", "xmark", "Close"),
        Icon("back", "뒤로", "chevron.left", "AutoMirrored.Rounded.ArrowBack"),
        Icon("expand", "펼침", "chevron.down", "ExpandMore"),
        Icon("check", "성공·확인", "checkmark.circle", "CheckCircle"),
        Icon("verified", "인증", "checkmark.seal.fill", "Verified"),
        Icon("error", "오류", "exclamationmark.triangle", "Warning"),
        Icon("info", "정보", "info.circle", "Info"),
        Icon("bell", "알림", "bell", "Notifications"),
        Icon("qr", "QR", "qrcode", "QrCode"),
        Icon("card", "명함", "person.text.rectangle", "Badge"),
        Icon("profile", "프로필", "person.crop.circle", "AccountCircle"),
        Icon("explore", "활동 둘러보기", "safari", "Explore"),
        Icon("checklist", "내 활동", "checklist", "Checklist"),
        Icon("inbox", "받은 명함", "tray.full", "Inbox"),
        Icon("share", "공유", "square.and.arrow.up", "Share"),
        Icon("map", "지도", "map", "Map"),
        Icon("phone", "전화", "phone", "Call"),
        Icon("mail", "메일", "envelope", "Mail"),
        Icon("attach", "첨부", "paperclip", "AttachFile"),
        Icon("photo", "사진", "photo", "Image"),
        Icon("eye", "눈", "eye", "Visibility"),
        Icon("lock", "잠금", "lock", "Lock"),
        Icon("device", "기기", "iphone", "Smartphone"),
        Icon("up", "위로", "arrow.up", "ArrowUpward"),
        Icon("organization", "조직", "building.2", "Business"),
        Icon("history", "변경 이력", "clock.arrow.circlepath", "History"),
        Icon("deadline", "마감", "flag", "Flag"),
        Icon("announcement", "발표", "megaphone", "Campaign"),
        Icon("interview", "면접", "person.wave.2", "RecordVoiceOver"),
        Icon("other", "기타(단계)", "pencil.line", "EditNote"),
        Icon("chat", "채팅", "bubble.left", "ChatBubble"),
        Icon("camera", "사진(연락처)", "camera", "CameraAlt"),
        Icon("link", "링크", "link", "Link"),
        Icon("unselected", "선택 안 됨", "circle", "RadioButtonUnchecked"),
        Icon("settings", "설정", "gearshape", "Settings"),
        Icon("send", "보내기·신청", "paperplane", "Send"),
        Icon("home", "홈", "house", "Home"),
    )

    private val byKey = ALL.associateBy { it.key }

    fun has(key: String): Boolean = key in byKey

    // 활동 종류의 기본 아이콘 키
    fun ofCategory(category: ActivityCategory): String = category.name.lowercase().takeIf(::has) ?: DEFAULT_CATEGORY

    // 분야 태그의 기본 아이콘 키(nameEn이 표에 있으면 그것, 없으면 tag)
    fun ofTag(nameEn: String?): String = nameEn?.takeIf(::has) ?: DEFAULT_TAG
}
