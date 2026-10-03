# 모바일 예시 이미지

2026-10-04. iOS·Android 클릭형 프로토타입의 공통 원본이다.
각 앱은 오프라인 실행을 위해 네이티브 이미지 리소스에 사본을 넣는다.

| 파일 | 용도 | 출처 |
| --- | --- | --- |
| `conference.png` | 첫 활동 썸네일·상세 사진 | 사용자가 지정한 `selected/01-current-flow/activity-detail-top.png`의 사진을 참조해 내장 imagegen으로 재구성 |
| `camp.png` | 메이커 캠프 썸네일·상세 사진 | `selected/01-current-flow/discovery.png` 두 번째 카드 사진 참조, 내장 imagegen |
| `meetup.png` | 밋업 썸네일·상세 사진 | 같은 탐색 시안 세 번째 카드 사진 참조, 내장 imagegen |
| `qr-example.png` | QR 화면·확대 예시 | macOS CoreImage `CIQRCodeGenerator`, correction M, 흰색 4모듈 여백, 24배 크기. 내용은 `https://example.com`이며 CoreImage로 다시 읽어 확인 |

사진은 실제 행사의 공식 자료가 아닌 생성된 디자인 예시다. QR은 개인 정보·인증 토큰·실제 명함 식별자를 포함하지 않는다.
휴대폰 테두리나 앱 화면 전체를 이미지로 사용하는 방식이 아니며, 앱 UI는 각 플랫폼의 컴포넌트로 구성한다.

## 사용한 프롬프트

내장 도구로 생성했으며 API 키나 별도 유료 API 호출은 사용하지 않았다.

### conference.png

> Edit target is the provided approved mobile app mockup. Produce ONLY the rectangular conference event photograph visible in the hero area of the mockup, as a clean standalone landscape photo asset, aspect 2:1. Reconstruct that exact composition as closely as possible: audience backs foreground, speaker on right stage, bright screen in center, warmly lit conference room. Exclude ALL phone frame, status bar, UI, buttons, cards, icons, header and app labels. Fill the whole image with conference photo, no white margin. This is a fictional design prototype asset. Keep the photographic style and scene framing. Do not add branding or new text; any small backdrop type can stay subtle. This output will be bundled into both native mobile apps, not displayed as a screenshot of an app.

### camp.png

> Edit target: approved mobile discovery mockup. Output ONLY a clean standalone landscape photograph reconstructing the SECOND card thumbnail (makers/student team seated at table, laptops, notebooks and hands discussing together), closely matching the source photo atmosphere and composition. Landscape 2:1, photo fills every pixel, no collage, no phone, no UI, no borders, no app labels, no watermark. Natural candid editorial photograph with warm daylight, same beige/cream tones, people are young adults. This is a fictional maker camp image bundled into a UI-only prototype.

### meetup.png

> Edit target: approved mobile discovery mockup. Output ONLY a clean standalone landscape photograph reconstructing the THIRD card thumbnail (front-end developer presenter in dark room, projected code screen behind them, audience heads in foreground). Preserve dark blue and natural warm tones and the photo composition as closely as possible. Landscape 2:1, photo fills every pixel, no phone frame, no application UI, no buttons, no borders, no collage. This is a fictional developer community meetup image bundled into a native UI prototype. No new logos or legible marketing text.
