# Dearby 웹

공식 출처를 확인한 국내 IT 컨퍼런스·연합동아리 탐색 웹입니다. 고등학생·대학생·취준생 모두 탐색할 수 있으며, 행사별 참가 자격은 별도로 표시합니다. 상단 검색, 좌측 탐색, 주제 칩, 16:9 썸네일과 회차별 참가 정보를 제공합니다.

**2026-09-24에 일회성으로 확인한 정적 스냅샷입니다.** 자동 크롤러나 실시간 등록 현황이 아닙니다. 미확인 필드는 `null`로 저장하고 화면에 미확인으로 표시합니다. 신청·결제는 공식 사이트에서 진행하며, Dearby에는 인증·신청 실행·결제·프로필·추천 학습·서버 API가 없습니다. 프로그램과 조직 스크랩은 현재 브라우저에 따로 저장됩니다.

## 실행과 검증

Node.js 22와 npm을 사용합니다.

```sh
npm ci
npm run dev -- --port 3000
npm run lint
npm run typecheck
npm test
npm run build
npx playwright install chromium webkit
npm run test:e2e
npm run test:dev
```

프로덕션은 build 다음 `npm run start -- --port 3000`으로 실행합니다. production E2E는 자체 3210 서버와 Chromium 1440×1000/390×844를, dev 회귀는 3211 서버와 Chromium/WebKit을 사용하고 종료 시 정리합니다. 개인 브라우저와 메인 3000 서버를 사용하지 않습니다.

[기획 결정](docs/product/conference-first-web-2026-09.md) · [기업/조직별 조사 목록](docs/research/korea-it-conferences-2026-09.md)

## 데이터와 출처

28개 프로그램·30개 회차 공고·26개 조직입니다. FEConf, 우아콘, NAVER DAN, if(kakao), Toss Makers, Tech-Verse, PyCon Korea, Spring Camp, Let’Swift, GopherCon Korea, AWS Summit Seoul, kt cloud summit, Droid Knights, DevFest Cloud x Seoul, DevFest Korea Android, Samsung AI Forum, Samsung Tech Conference, DEVIEW를 포함합니다.

- 행사별 원문 URL·확인일·확인 근거는 `src/features/catalog/data.ts`의 `notices[].sources`와 상세의 공식 출처에 있습니다.
- 컨퍼런스 공식 공유이미지 18개와 대표영역 캡처 4개를 로컬 저장했습니다. [이미지 출처 목록](public/conferences/SOURCES.md)에 원본 URL과 확인일, 대체 이유를 기록했습니다. 사용 허가나 자유 재배포 가능 여부를 확인한 것은 아닙니다.
- `current`는 **확인된 대표 회차**이며 현재 모집 중이라는 뜻이 아닙니다. if(kakao)는 2026/2025, Spring Camp는 2026/2025 회차를 분리합니다. 알려진 최신 회차만 대표로 지정하며 다음 연도 회차를 만들어 넣지 않습니다.
- `status`는 open/scheduled/closed/ended/unknown으로 구분합니다. 날짜가 지난 행사는 종료로 표시하며 남은 구매 버튼이나 온디맨드 등록을 참가 접수로 해석하지 않습니다. 현재 참가 신청 중은 if(kakao)26·우아콘2026·Droid Knights2026입니다.
- 비용·자격·신청 기간은 공식 확인값만 표시합니다. 예: if(kakao)26 만18세 이상·선정자, 우아콘 무료·추첨 선정, Droid Knights 일반 69,000원. 삼성 AI 포럼의 일반 온라인 참가 자격·등록기간은 미확인입니다. Toss는 공식 종료 보도자료로 2025년7월23–25일 코엑스 개최를 보완했으며 비용·자격은 미확인입니다. DEVIEW2023은 2024년 DAN 통합 이전 아카이브로 구분합니다.
- 토픽 분류는 확인한 세션 주제를 Dearby 분야에 매핑한 가역적인 선택입니다. 모든 연사 발표를 참가자의 발표 경험으로 취급하지 않습니다. 멘토링·실습·교류도 확인 근거와 별도 참가 조건을 표시합니다.
- 이전 가상 프로그램 ID는 실제 행사로 임의 연결하지 않습니다. 기존 저장 파서가 더 이상 존재하지 않는 ID를 제외하므로 가상 샘플 스크랩은 실제 목록에 나타나지 않습니다.

## 구조와 동작

`src/app`는 라우팅, `features/catalog`는 공식 스냅샷·검색·공고 단위 필터·UI, `features/saved`는 로컬 저장과 복구, `components`는 셸을 소유합니다. 이미지에는 Next Image의 로컬 최적화를 사용하고 첫 줄만 즉시 로딩합니다. 외부 폰트 요청은 없습니다.

분야 OR(모두 포함이면 AND), 경험 OR, 두 유형 간 AND를 **같은 대표 공고 안에서** 판정합니다. 서로 다른 활동의 속성이나 과거 회차 근거를 결합하지 않습니다. 검색은 행사명·설명·종류·대표 연도·회사/조직명을 포함하고 결과 수는 고유 프로그램 수입니다. 검색·프로그램 유형·분야·경험·우선 경험·접수 필터는 URL에 보존됩니다.

0건 대안은 선택한 AND 분야 중 실제 공고가 함께 충족하는 최대 조합을 후보로 삼습니다. OR 그룹은 전체 유지/해제하고 무조건 전체 조회는 초기화로 제공합니다. 최대 4개를 유지 조건 수·결과 수 순으로 제안하며 자동 적용하지 않고 적용 후 되돌리기를 제공합니다. 12개 분야의 불필요한 전체 부분집합 계산을 피합니다.

저장 실패·손상·접근 차단을 구분하고 재시도/명시적 초기화, 다른 탭 동기화를 유지합니다. 각 소비자의 `useSyncExternalStore` 서버 스냅샷으로 새 페이지 직접 진입 hydration을 검증합니다. 접근성 검사는 axe WCAG AA와 키보드 흐름을 포함하지만 수동 스크린리더·실기기 검증을 대신하지 않습니다.

최신 검증과 인계는 [웹 인계](docs/context/web-implementation-and-handoff.md)에 기록합니다. 제품 정본·조사 문서·CI는 메인 세션 소유입니다.

## 기업 로고 아바타

우아한형제들·NAVER·Kakao·Toss·LY·AWS·kt cloud·Samsung의 공식 기업 로고를 로컬 저장해 카드·상세·스크랩 조직 목록에 공통 적용합니다. [공식 출처와 확인일](public/organizations/SOURCES.md)을 기록했습니다. 커뮤니티는 이니셜을 유지하고 이미지 실패도 이니셜로 대체합니다. 기존 35/44px 크기·흰 배경·contain으로 비율을 유지하며, 인접 조직명과 중복해서 읽지 않도록 로고는 장식 이미지입니다. 긴 워드마크는 작은 크기로 표시되므로 조직명을 함께 제공합니다.

국내 행사 후보 발굴에 [Dev-Event](https://github.com/brave-people/Dev-Event)와 [dev-conf-replay](https://github.com/hibuz/dev-conf-replay)를 참고했습니다. 공식 출처로 재확인한 UbuCon × MiniDebConf, KCD × Ceph × OpenInfra, REAL Summit, SK AI Summit을 추가했습니다. 공동행사는 한 카드로 묶고 종료 여부·과거회차·미확인 필드는 공식 출처를 기준으로 표시합니다. Linux 분야 필터도 제공합니다.

## 선발형 연합동아리

SOPT·피로그래밍·COTATO·YAPP·디프만·Mash-Up 6개를 추가했습니다. [dev-club-schedule](https://github.com/itsChrisJang/dev-club-schedule)은 후보 발굴에만 참고하고 모집 사실은 공식 사이트에서 확인했습니다. 동아리는 기수별 지원 대상·조건·선발 절차·회비·활동 일정을 표시하며, 컨퍼런스 등록 문구와 구분합니다. 유형 선택은 URL·검색·0건 대안·스크랩 필터와 함께 동작합니다. [동아리 이미지 출처](public/clubs/SOURCES.md)에 공식 OG 5개/대표영역 캡처 1개를 기록했습니다.

디프만은 새로 확인된 19기(10/2–8 접수 예정), SOPT는 확인된 39기 OB 서버 전형만 등록하며 신규 YB 전형으로 해석하지 않습니다. YAPP 28기는 모집 마감, 피로그래밍 25기는 연도가 없는 모집 일정으로 날짜/상태 미확인, COTATO는 기수/접수상태 미확인, Mash-Up은 날짜가 접속일로 반복 표시되어 접수상태 미확인입니다. 현재 모집중을 추측하지 않으며 DND는 상세 선발 절차 미확인으로 이번 추가에서 제외했습니다.
