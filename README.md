# Dearby

모집 중 활동을 발견하고, 공개 범위를 고른 명함을 활동 맥락과 함께 교환·보관하는 iOS·Android 서비스입니다.

2026-09-27 확정 Seed에 따라 네이티브 앱과 서버를 구현 중입니다. 이전 Next.js 실행 코드는 제거했습니다. 전체 서비스나 운영 연동이 완료된 상태는 아닙니다.

## 정본과 작업

- [확정 제품 명세](docs/product/native-spec-2026-09.md)
- [공통 API 계약](shared/contracts/native-v1.md)
- [현재 작업·검증·차단 사항](docs/context/native-restart-2026-09-27.md)
- [iOS #37](https://github.com/fixabley/dearby/issues/37), [Android #38](https://github.com/fixabley/dearby/issues/38), [API #39](https://github.com/fixabley/dearby/issues/39)

실행: [iOS](apps/ios/README.md), [Android](apps/android/README.md), [API](apps/dearby-api/README.md). 실제 화면과 검증 증거: [iOS](apps/ios/docs/evidence/README.md), [Android](apps/android/docs/VERIFICATION.md), [통합 검증](docs/implementation/verification-matrix.md).

프로필·선택 공개 명함·기기 ID 저장·선택 가져오기·양방향 교환은 격리된 실제 API로 검증했습니다. 기본 앱의 API 주소는 미설정이며 운영 서버·메일·공개 HTTPS 링크를 연결해야 외부 사용자가 사용할 수 있습니다. [통합 PR #40](https://github.com/fixabley/dearby/pull/40)은 Draft입니다.

## 조사 자료

`shared/data/catalog-snapshot-2026-09-24.json`은 과거 공식 출처 조사 스냅샷이며 실시간 모집 현황이 아닙니다. 프로그램·공고·조직 관계와 확인일을 보존했습니다. `shared/assets`의 이미지 출처 문서를 함께 확인하세요. 이미지의 자유 재배포 허가를 확인했다는 뜻은 아닙니다.

개발 중 차단 사항은 GitHub 이슈에 기록하고 독립 작업을 계속합니다. 미검증 연동을 성공으로 표시하지 않습니다.
