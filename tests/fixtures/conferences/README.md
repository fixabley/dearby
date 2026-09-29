# 공식 정보 기반 IT 컨퍼런스 목 데이터

사용자 요청(2026-09-29)에 따라 탐색·상세·신청 화면을 볼 수 있는 **개발 전용** 데이터5건을 수집했다. 운영 DB와 자동 수집기는 변경하지 않는다. `data.json`은 실제 행사 정보, `serve.py`는 기존 catalog-v1 DTO로 변환하는 명시적 localhost 목 서버다.

| 행사 | 공식 출처 | 확인한 내용 |
| --- | --- | --- |
| FEConf2026 | https://2026.feconf.kr/ | 10월24일10시 입장, 롯데타워31층, 티켓 오픈 예정. 실제 수집기 갱신 결과2026-09-29T01:06:10Z 참고. 종료 시각 불명 |
| if(kakao)26 | https://if.kakao.com/2026 | 10월13–14일, 카카오AI캠퍼스, 무료, 신청 마감, 선정 결과10월1일 이후 |
| PyCon Korea2026 | https://2026.pycon.kr/?lang=en-US | 8월15–17일, 동국대학교 신공학관, 티켓 마감 |
| NAVER DAN25 | https://dan.naver.com/25 | 2025년11월6–7일, 코엑스, 종료·영상 공개 |
| SLASH24 | https://toss.im/slash-24 / https://toss.im/tossfeed/article/slash24-conference | 2024년9월12일 코엑스, 종료·영상 공개 |

DAN DAY1 키노트10–12시/Deep Dive13:30–16:10은 https://dan.naver.com/25/sessions/698 의 공개 시간표 일부다. 전체 행사 시간을 의미하지 않는다. 미확인 시각은 null/빈 일정으로 유지해 캘린더 결과를 꾸미지 않는다.

## 실행

```sh
python3 tests/fixtures/conferences/serve.py
```

- iOS Debug: `DEARBY_API_URL=http://127.0.0.1:58764`로 빌드.
- Android에서 쓰려면 해당 에뮬레이터에 `adb reverse tcp:58764 tcp:58764`, `-PdearbyApiUrl=http://127.0.0.1:58764`로 별도 Debug 빌드.
- 종료 후 실제 API로 돌아갈 때 해당 API origin으로 다시 빌드한다. production 기본 URL/소스/데이터는 변경하지 않았다.

기존 앱의 모집중 필터를 통과시키기 위해 목 응답만 open/isRecruiting/verified와 단기 유효 시각을 생성한다. **공식 모집 상태/검증 시각이 아니다.** 모든 제목에 `[목 데이터]`, 요약 첫머리에 데모 안내, 상세 sourceNote에 생성 시각과 실제 상태를 명시했다. 목 전용 UUID namespace로 실제 활동의 신청 기록과 충돌하지 않는다. 신청 URL은 제출 기능이 없는 로컬 안내 페이지이고 공식 출처 URL은 실제 사이트다. 과거 행사를 미래로 이동하지 않는다.

## 검증

2026-09-29:5건 ID/참조/목 라벨/데모 신청 HTTP 검증 통과. iOS Debug build+install+launch 성공, iPhone17의 실제 UI에서 목록 표시 확인. 플랫폼 production 코드 변경 없음. Android 설치·UI 검증은 이번 데이터 작업에서 실행하지 않았다. Ponytail: 표준 라이브러리 HTTP/JSON/UUID만 사용, 별도 의존성/범용 수집 프레임워크 없음. Lean already. Ship.
