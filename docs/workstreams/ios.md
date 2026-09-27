# iOS 담당

2026-09-27 issue #46, `feat/ios-activities` from `dcb590f`. [현재 인계](../context/ios-implementation-and-handoff.md), [활동 계약/검증 진행](../../apps/ios/docs/catalog-implementation.md).

실제 catalog HTTP·발견/저장/상세·신청 자기기록·등록 활동 교환 선택 구현. 단위 27/구조16/SwiftLint 통과, 실제 로컬 API decode와 디스크 복원 통과. 실제 활동 XCUITest의 신청/나중에/저장/재시작/수정 및 기존 profile·QR·wallet UI 회귀도 통과. Debug/Release build 통과. 검증 정본과 현재 한계는 인계 참조. Simulator ad-hoc signing 유지. 사용자 요청에 따라 완료 후 세션 retain, push/PR/통합은 coordinator 담당.
