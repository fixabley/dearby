# Card interactions — 2026-09-27

Received card contacts retain typed label/value/kind and a validated target in display State. Expanded cards expose icon+label native buttons at least 48dp high. Phone opens the dialer, email opens a compose destination, HTTPS opens an external handler, and Kakao IDs/unsupported values copy. No phone call or email is automatically sent. The OS adapter revalidates at the effect boundary; javascript/intent/http URLs, userinfo and unexpected ports cannot launch.

Histories preserve separate dates, title, role and description in a timeline. Header coordinates stay fixed across expansion. Collapsed cards now wrap their content rather than painting a full-height empty card. White/teal selection containers replace the Material default lavender. QR's new-card tile is rectangular, its help text is outside the QR card, and tap enlargement is a separate QR-only dialog.

Verification: ContactTargetTest covers allowed telephone/email/HTTPS and unsafe input copy-only behavior. ScreenTest checks explicit contact callback, unchanged header top coordinate, no automatic send on card selection, privacy defaults, import selection, guest profile login gate and QR menu/invitation. Actual screen captures live in docs/evidence/ui-final; those are isolated UI fixtures, not backend accounts.

실제 서버 화면 검사에서 UTC 날짜를 단순 잘라 표시하면 KST 자정 경계가 하루 어긋나는 것을 확인했다. app/providers의 표시 변환을 기기 시간대로 변경하고 UTC↔Asia/Seoul 자정 회귀를 추가했다. 원본 receivedAt/savedAt UTC 값은 변경하지 않는다.
