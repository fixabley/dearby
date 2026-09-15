# 상세 날짜/시간 · 2026-09-15 후속

사용자 참고 이미지의 요일 뒤 줄바꿈은 Android의 기존 DetailMetadata Column이 이미 충족한다. 날짜는 bodyLarge/medium, 시간은 다음 줄 bodyMedium이며 최대 줄 수나 fontScale clamp가 없다. 같은 날은 날짜 한 번 + 시간 범위, 여러 날은 각각의 날짜/시각 행을 유지한다.

신청 및 활동에서 Asia/Seoul의 중복 보조 라벨 ‘한국 시간’만 숨긴다. Europe/London 등 다른 시간대 표시, 원본/sourcezone/precision/불확실성/AX 설명, 카드 및 Calendar export는 바꾸지 않는다. DetailTimezoneTest로 한국 라벨 제거, 원본 시각 semantics, 다른 zone 표시를 확인한다.
