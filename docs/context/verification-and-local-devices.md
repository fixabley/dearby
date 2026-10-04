# 검증 기록과 실행 안내

2026-10-04 문서 정리 시점의 안내다. 아래 앱 검사·기기 설치는 기존 실행 기록이며 이번 문서 수정으로 재실행한 결과가 아니다.

| 확인할 대상 | 근거 | 재현 안내 |
| --- | --- | --- |
| iOS 화면·상태 | [플랫폼 인계](ios-ui-prototype.md), [최종 캡처](../../apps/ios/docs/evidence/ui-prototype-selected/README.md) | [iOS README](../../apps/ios/README.md) |
| Android 화면·상태 | [플랫폼 인계](android-ui-prototype.md), [검증 보고서](../../apps/android/docs/VERIFICATION.md), [최종 캡처](../../apps/android/evidence/selected-card-flows/README.md) | [Android README](../../apps/android/README.md) |
| 통합·파일 정리 | [PR73 검사 실행](https://github.com/fixabley/dearby/actions/runs/37164800909) | 원격의 해당 커밋 기준 |
| 실제 기기 설치 | [모바일 통합 기록](mobile-ui-prototype.md#진행-상태) | 기존 설치 결과이며 현재 연결 여부는 다시 확인 |
| 문서·파일 정리 | [이번 검사 결과](documentation-maintenance.md) | 링크·삭제 참조·변경 범위 검사 |

PR73의 API·Android·iOS 필수 검사는 성공했다. Supabase Preview는 건너뛴 항목이며 통과로 세지 않는다. 이 결과는 PR73 당시 커밋에만 적용된다.

기기 접근·Wi-Fi 연결·로그인·운영 HTTPS·TestFlight 처리 상태는 문서만으로 현재 유효성을 판단하지 않는다. 저장소 밖 서명 산출물은 모바일 통합 기록의 위치를 참고하며 프로젝트 안으로 옮기지 않는다.

문서 정리 시작 시 root에 있던 Xcode 프로젝트·scheme 삭제는 보존했다. 이번에는 네이티브 빌드·실기기 재설치·서버 배포를 수행하지 않는다. 실제 서비스의 저장·권한·전송 검증은 오프라인 모바일 화면 검사와 구분한다.

[이전 검증·기기 기록](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/verification-and-local-devices.md)은 당시 실행의 이력이다. 오래된 checkout 경로·명령은 현재 앱 README와 대조한 후 사용한다.

## 기록을 갱신할 때

모바일 검사는 위 표의 플랫폼 검증 보고서나 캡처 README를 갱신하고, 실행 날짜·대상 커밋·명령·결과·미검증 범위와 원본 로그 위치를 적는다. 플랫폼 인계에는 요약과 링크만 남긴다. 문서만 바꾼 검사는 해당 정리 기록에 남긴다. 기존 결과를 덮어야 한다면 이전 커밋의 고정 링크를 보존한다. 새 파일은 독립된 근거가 필요할 때만 만든다.
