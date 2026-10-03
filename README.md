# Dearby

활동 탐색과 명함 공유를 다루는 프로젝트입니다. **현재 iOS·Android는 디자인과 화면 이동을 확인하는 오프라인 프로토타입입니다.**

모바일에서는 고정 예시로 활동 탐색·상세·신청 안내·일정 겹침과 프로필·명함·QR·명함함 화면을 확인합니다. 실제 서버·로그인·영구 저장·기기 캘린더 연결은 제거했고, 서버·사용자 웹·어드민은 별도로 보존합니다. [모바일 프로토타입 범위와 복구 지점](docs/context/mobile-ui-prototype.md)을 먼저 확인하세요.

## 정본과 작업

- [확정 제품 명세](docs/product/native-spec-2026-09.md)
- [공통 API 계약](shared/contracts/native-v1.md)
- [현재 모바일 작업·검증](docs/context/mobile-ui-prototype.md)
- [시안별 화면과 공통 컴포넌트 위치](docs/design/mobile-prototype-reference-map.md)
- [iOS #37](https://github.com/fixabley/dearby/issues/37), [Android #38](https://github.com/fixabley/dearby/issues/38), [API #39](https://github.com/fixabley/dearby/issues/39)

실행: [iOS](apps/ios/README.md), [Android](apps/android/README.md), [API](apps/dearby-api/README.md). 현재 모바일 검증: [iOS](docs/context/ios-ui-prototype.md), [Android](docs/context/android-ui-prototype.md). 과거 서비스 [통합 검증](docs/implementation/verification-matrix.md)은 별도 이력입니다.

과거 실제 서비스 검증·명함 교환 계약은 아래 문서와 Git 이력에 보존합니다. 과거 기능의 구현 기록을 현재 모바일 프로토타입의 기능으로 해석하지 않습니다.

## 조사 자료

`shared/data/catalog-snapshot-2026-09-24.json`은 과거 공식 출처 조사 스냅샷이며 실시간 모집 현황이 아닙니다. 프로그램·공고·조직 관계와 확인일을 보존했습니다. `shared/assets`의 이미지 출처 문서를 함께 확인하세요. 이미지의 자유 재배포 허가를 확인했다는 뜻은 아닙니다.

개발 중 차단 사항은 GitHub 이슈에 기록하고 독립 작업을 계속합니다. 미검증 연동을 성공으로 표시하지 않습니다.
