# API 세션 인계

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](../context/coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

2026-09-14 기준. 초기 인계 문서 작성 완료 후 coordinator 검토와 다음 지시를 기다린다. 새 기능 구현은 요청되지 않았으며 이 문서의 미구현 항목은 착수 계획이 아니다.

## 세션과 담당 범위

- 현재 worktree: `/Users/jominjun/Documents/dearby/dearby-api`
- 현재 브랜치: `fixabley/dearby-api`
- 기준: `main` 및 현재 HEAD `79ac2816e60154d4f48d1ad59c8e1f593d8ee9bc` (`79ac281`)
- 플랫폼 담당 경로: [`apps/dearby-api/`](../../apps/dearby-api/) — NestJS API 서버의 소스, 테스트, 실행 설정.
- 이번 인계에서 수정하는 파일: `docs/workstreams/api.md` 하나.
- API·Android·iOS는 각각 독립 worktree와 Orca 세션에서 작업하며 coordinator가 진행과 공통 변경을 조율한다. 다른 worktree, 다른 플랫폼, 공통 명세와 루트 문서는 이 세션에서 수정하지 않는다. 이번 작업은 커밋·푸시하지 않는다.

## 구현 현황과 제품 경계

API는 NestJS CLI로 생성한 초기 scaffold다. [API README](../../apps/dearby-api/README.md)와 [package.json](../../apps/dearby-api/package.json)에 NestJS 12, Express, TypeScript ESM, Vitest, oxlint 구성이 있다. `src/main.ts`가 서버를 생성하고 `PORT` 또는 기본 3000 포트로 수신한다. `AppModule`에는 기본 컨트롤러와 서비스만 등록되어 있으며 `GET /`는 `Hello World!`를 반환하도록 작성되어 있다. 기본 컨트롤러 단위 테스트와 같은 응답 및 HTTP 200을 검사하는 E2E 테스트가 각각 있다. 이번에 서버를 실행해 확인한 결과는 아니다.

제품 문서상 Android는 Kotlin Jetpack Compose(API 31+), iOS는 SwiftUI(iOS 26+)이며, 두 앱은 오프라인 JSON 샘플만 사용한다. 공고 카드 세로 넘김, 더블탭·저장 버튼, 조직 ID별 즐겨찾기와 로컬 보관이 구현되어 있다. 샘플 5건 중 학사 행정 1건을 제외한 4건을 피드에 표시한다. Android는 SharedPreferences, iOS는 UserDefaults를 사용하며 API 저장 기능은 없다.

공통 데이터에서 유지할 의미는 다음과 같다.

- `Organization`과 개별 `Activity`는 별도 ID를 갖는다. 조직의 `parentOrganizationId`, 활동의 `categoryPath`, 행사 학교 등의 `contexts`, 저장 대상인 `favoriteOrganizationId`는 서로 다른 관계다. 게시자·운영자·문의처의 역할인 `organizationLinks`도 부모 관계와 구별한다.
- 한국농어촌공사 공고는 `krc`, DB손해보험 공고는 `db-insurance`를 저장한다. 충북대학교는 행사 맥락이고 대학일자리센터는 운영자다. 학교를 기업 하위 조직으로 만들거나 학교 맥락으로 지원 자격을 추정하지 않는다. 샘플의 한국농어촌공사 `kind`는 `institution`이며 이 인계에서 바꾸지 않는다.
- 영남권 대회는 교육원 `yeongnam-ai-security` 아래 지속 프로그램 `yeongnam-cyber-defense`를 저장한다. 제2회는 공고의 `edition: 2`다. 이 부모 관계는 사용자 지정 서비스 분류이며, 원문의 교육원 `contact` 역할을 단독 주관으로 바꾸지 않는다.
- 회차·연도가 달라도 같은 조직 ID를 유지하고 공고 ID는 분리한다. 조직 저장은 부모나 자식의 자동 저장을 뜻하지 않는다.
- 승인 사례 기반 관심 대상 추론은 공통 스크립트에 있으며 API 엔드포인트가 아니다. `approved`, `suggested`, `needs_review`는 관심 대상 매핑 상태이고 모집 정보의 최신성이나 개인의 지원 가능 판정이 아니다.

## 실행·검증 명령

아래는 기존 README와 npm scripts에서 확인한 재현용 명령이다. 이번 인계에서는 설치, 서버 실행, 빌드, lint, 테스트 및 시뮬레이터 조작을 실행하지 않았다. API README의 검증 환경 표기는 Node.js 26.5.0 / npm 12.0.2이며, 이번 세션의 설치 버전을 확인한 것은 아니다.

서버 설치와 개발 실행은 worktree 루트에서:

```sh
cd apps/dearby-api
npm ci
npm run start:dev
# 포트 변경이 필요한 경우
PORT=3001 npm run start:dev
```

서버 검증은 `apps/dearby-api/`에서:

```sh
npm run build
npm run lint
npm test
npm run test:e2e
# 빌드 후 실행
npm run start:prod
```

공통 데이터 검증·추론은 API 디렉터리가 아닌 worktree 루트에서 실행하는 별도 명령이다. 루트 `npm test`는 서버 테스트가 아니다. 의존성 준비에는 루트 `npm ci`가 필요할 수 있으며 이번에는 실행하지 않았다.

```sh
npm test
npm run samples:check
npm run samples:infer
```

`python3 scripts/sync-activity-samples.py`는 두 앱의 JSON 리소스를 실제로 덮어쓰므로 이번 담당 범위에서 실행하지 않는다. 공통 변경이 승인된 경우 coordinator가 담당자와 동기화·검증을 조율한다.

## 기존 검증 기록과 이번 확인

| 구분 | 근거와 확인 수준 |
| --- | --- |
| 기존 공통 검증 | [반복 01](../product/iteration-01.md)에 공통 데이터 테스트 13건 통과 기록이 있다. 스키마·참조·조직 트리·관심 대상 추론·앱 리소스 일치 등을 다룬다. 이번 재실행 결과는 아니다. |
| 기존 앱 검증 | 같은 문서에 iOS Simulator Debug 빌드·화면 실행·Swift 저장 검증과 Android Debug 빌드·Lint·API 36.1 계측 테스트 3건 통과 기록이 있다. iOS 자동 제스처 UI 테스트는 없다고 명시되어 있다. 이 세션에서 재검증하지 않았다. |
| 기존 API 근거 | 초기화 커밋 `1728fa4`, API README의 환경·명령, 기본 단위·E2E 테스트 코드 존재를 확인했다. 읽은 기록에는 API 각 검증 명령의 구체적 실행 결과가 없어 통과했다고 단정하지 않는다. |
| 이번 인계 확인 | Git 기준 커밋·브랜치·worktree와 시작 시 변경 없음, 서버 소스·테스트·npm scripts, 공통 샘플·스키마·규칙 및 제품 문서를 읽었다. 문서 생성 후 Git diff와 변경 경로를 확인했다. 제품 기능 테스트는 실행하지 않았다. |

## 알려진 한계와 대기 상태

DB·인증·로그인·공고/조직 도메인 API·클라이언트 API 연결·기기 간 동기화는 미구현이다. 원문 자동 추출·자동 수집, 개인화 추천·자동 자격 판정, Q&A·커피챗·Dearby 크레딧도 미구현이다. 조직 트리 탐색 UI와 운영자 검토 화면은 없다.

샘플은 원문을 수동 검토한 스냅샷이다. 원문 충돌, 첨부 미검토, 일부 대상·장소 미확인 사항을 보존하며 실시간 모집 정보로 보장하지 않는다. 멘토링 등의 잠정 관심 대상과 사용자 승인 사례를 구별한다. 외부 CIEAT 마일리지는 Dearby 크레딧이 아니다.

현재 상태는 **인계 문서 작성 완료·coordinator 검토 및 다음 지시 대기**다. 미구현 항목에서 임의로 새 요구사항을 만들거나 구현을 시작하지 않는다.

## 공통 변경 요청 절차

API 명세 변경이 필요하면 변경 이유, 대상 필드·경로, 기존/변경 예시, Android·iOS 영향 및 필요한 검증을 먼저 coordinator에게 Orca orchestration으로 전달한다. 진행 중인 Dispatch의 live preamble에 있는 `ask` 절차를 사용하고, 공통 파일이나 다른 플랫폼을 먼저 수정하지 않는다. coordinator와 명세·작업 소유권·반영 순서를 조율한 뒤 새로 허용된 범위만 수행한다. 완료한 Dispatch의 식별자는 후속 작업에 재사용하지 않는다.

## 관련 문서와 기준 데이터

- [활동 공고 규격·원문 검토](../product/activity-data-v1.md)
- [관심 대상 선정·분류 규칙](../product/interest-target-rules.md)
- [반복 01 구현·기존 검증·남은 결정](../product/iteration-01.md)
- [공통 샘플](../../shared/contracts/activities/sample.json), [JSON Schema](../../shared/contracts/activities/schema.json), [승인 사례·대상 규칙](../../shared/contracts/activities/target-rules.json)
- [API 실행 안내](../../apps/dearby-api/README.md), [모노레포 운영](../monorepo.md)
