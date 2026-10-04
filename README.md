# Dearby

활동 탐색과 명함 공유를 다루는 프로젝트입니다. **현재 iOS·Android는 디자인과 화면 이동을 확인하는 오프라인 프로토타입입니다.** 고정 예시와 메모리 상태로 활동·일정 겹침·프로필·명함·QR 화면을 확인합니다.

## 시작하기

1. [현재 모바일 범위와 화면 흐름](docs/context/mobile-ui-prototype.md)을 읽습니다.
2. [iOS 실행](apps/ios/README.md) 또는 [Android 실행](apps/android/README.md)을 따릅니다.
3. 화면을 수정할 때는 [시안·공통 컴포넌트 적용표](docs/design/mobile-prototype-reference-map.md)를 참고합니다.
4. [검증 기록과 한계](docs/context/verification-and-local-devices.md), [현재 작업](docs/context/coordinator-current-task-and-decisions.md)을 확인합니다.

## 프로젝트 구성

| 경로 | 역할 |
| --- | --- |
| `apps/ios`, `apps/android` | 오프라인 모바일 프로토타입 |
| `shared/assets/prototype` | 양쪽 앱이 사용하는 이미지 원본 |
| `apps/dearby-api` | 별도 보존한 API 서버. [실행 안내](apps/dearby-api/README.md) |
| `apps/web`, `apps/admin` | 별도 보존한 사용자 웹·어드민 |
| `docs/context` | [모바일·서비스 운영·과거 기록 목차](docs/context/README.md) |

모바일의 실제 인증·서버·영구 저장·기기 캘린더·카메라 연결은 제거했습니다. 서버·웹·어드민·수집 워커와 운영 데이터는 보존합니다. [전체 서비스 제품 요구](docs/product/native-spec-2026-09.md)와 [API 계약](shared/contracts/native-v1.md)은 서비스 연결을 검토할 때 참고하며, 현재 모바일에서 모두 구현된 기능을 뜻하지 않습니다.

## 조사 자료

`shared/data/catalog-snapshot-2026-09-24.json`은 과거 공식 출처 조사 스냅샷입니다. 프로그램·공고·조직 관계와 확인일을 보존하며 실시간 모집 현황은 아닙니다. 이미지 사용 시 [프로토타입 자산](shared/assets/prototype/README.md), [컨퍼런스](shared/assets/conferences/SOURCES.md), [동아리](shared/assets/clubs/SOURCES.md), [조직](shared/assets/organizations/SOURCES.md)의 출처를 확인하세요. 출처 기록이 자유 재배포 허가를 의미하지는 않습니다.
