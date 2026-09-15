# API — 현재 범위와 인계

갱신: 2026-09-14. 담당 경로는 `apps/dearby-api/`다. #1·#2는 모바일 앱 작업이며 새 API 기능은 배정하지 않았다. 이전 API worktree에는 미병합 커밋이 없었고 세션·폴더는 정리했다.

## 구현 현황

NestJS CLI 초기 scaffold다. package.json에는 NestJS 12·Express·TypeScript ESM·Vitest·oxlint 구성이 있다. 기본 AppModule/Controller/Service와 GET / → Hello World! 및 기본 단위/E2E 테스트가 있다. 상세는 [API README](../../apps/dearby-api/README.md)와 [package.json](../../apps/dearby-api/package.json)을 따른다.

공고·조직 API, DB, 인증, 로그인, 클라이언트 네트워크 연결과 기기 간 동기화는 미구현이다. 두 앱은 mock 공급 경계와 로컬 캐시를 사용한다. Q&A·커피챗·크레딧·자동 수집·추천은 제품 구상이며 이번 작업의 구현 범위가 아니다.

## 재개 시 확인

API를 구현할 때 [공통 데이터 규칙](shared-data-and-source-decisions.md)의 조직/공고 ID·관계·출처·불확실성을 보존한다. 공통 계약 변경은 메인에서 양 플랫폼 영향과 반영 순서를 조율한다. 다른 플랫폼이나 공통 파일을 담당자가 동시에 임의 수정하지 않는다.

`apps/dearby-api`에서 npm ci 후 npm run start:dev로 실행한다. PORT 미지정 시 기본 3000이다. 검증 명령은 npm run build, npm run lint, npm test, npm run test:e2e다. 루트 npm test는 공통 데이터 테스트이며 API 테스트가 아니다.

이번 컨텍스트 정리에서 서버 실행·빌드·테스트는 수행하지 않았다. API 검증의 통과 여부는 별도로 확인해야 한다.
