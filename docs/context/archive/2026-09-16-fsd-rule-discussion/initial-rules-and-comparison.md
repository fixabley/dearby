# 도메인 중심 FSD 규칙 변경 초안

2026-09-16 사용자 요청. 아래 명시 사항은 이전 구조 규칙보다 우선하는 변경 의도다. 의존 허용표와 App의 의미 확인 전에는 기존 검사나 구현을 변경하지 않는다.

## 사용자가 지정한 책임

- Pages는 Widgets를 조립해 화면을 만든다. Pages/domain 바로 아래에 파일을 두고 domain 폴더 자체를 UI로 취급한다.
- Widgets는 Features의 동작과 Entities의 표현 컴포넌트를 조립하고 함수를 주입한다.
- Features는 동작을 위한 API, protocol, repository 등을 둔다.
- Entities는 도메인 구조체를 받을 수 있는 순수 컴포넌트(공고 카드·일정 박스 템플릿)를 Shared로 조립한다. 동작 구현은 Features에 둔다.
- Shared는 기본 컴포넌트와 디자인 설정만 둔다.
- 각 도메인 레이어의 첫 하위 폴더는 user, notice, favorite, schedule 등 domain으로 제한한다. 그 아래는 ui/model/apis만 허용한다. Pages는 세그먼트 폴더를 두지 않는다.
- apps 폴더는 딥링크와 연결되는 라우팅 구조에 맞춘다.
- import를 하위 2계층으로 제한한다고 표현했으며 명시 예시는 Pages→Widgets만 허용이다.

## 확인 중인 경계

1. 레이어별 허용표: App→Pages, Pages→Widgets, Widgets→Features·Entities, Features→Entities, Entities→Shared로 해석하는지. Shared 직접 참조 예외 여부.
2. 확정: apps는 저장소 루트가 아니라 각 클라이언트 내부 App/app 레이어다.
3. 엄격한 App→Pages 제한과 앱 전체 repository/cache 수명 조립의 관계는 최종 표에 명시해야 한다. 필요한 예외를 암묵적으로 추가하지 않는다.

## 기존 구현에서 달라질 부분

- Widgets/domain/widget 동위 파일 규칙은 Widgets/domain/ui|model|apis로 바뀐다.
- Pages/domain/UI|Model 배치는 domain 직하로 바뀐다.
- Entities의 repository/cache는 동작 책임인 Features로 이동 대상이다.
- Shared/Lib의 도메인·시간 계산·OS 연결 값은 책임에 맞는 domain/model 또는 Features로 이동 대상이다.
- Entities UI의 원본 도메인 Model 입력은 허용하고, Widgets가 동작을 주입하는 경계에 맞춰 기존 일괄 raw Model/Feature 참조 금지를 재설계한다.
- Swift 단일 모듈에서는 import 문뿐 아니라 타입·함수 참조도 검사해야 한다. 구문 검사와 lexical 검사 한계를 유지하고 새 규칙의 정상/위반 fixture를 둔다.
- 기존 화면·동작·캐시/즐겨찾기 단일 소유, 데이터 호환성은 별도 변경 요청이 없으므로 유지한다.

## 참고 문서 비교 · 2026-09-16

사용자가 https://fsd.how/docs/get-started/overview/를 참고해 본인의 이해와 차이를 먼저 설명해 달라고 요청했다. 앞선 엄격한 의존표는 승인되지 않았다. 코드/검사 변경 보류.

- FSD import 규칙은 모든 하위 레이어 허용이며 인접 1~2개 제한이 아니다. 앞서 assistant가 제안한 App→Pages / Pages→Widgets 전용 표는 프로젝트 커스텀 제안으로 정정한다.
- App/Shared에는 domain slice가 없고 목적별 segments를 직접 둔다. Pages도 slice 아래 ui/model/api 등 segment가 기본이다. api가 관례이며 apis 또는 3개만 허용은 커스텀 규칙이다.
- Entities는 모델/검증/저장/API/UI를 포함할 수 있다. Features는 모든 함수·repository가 아니라 재사용되는 사용자 가치 행동이며 UI도 포함할 수 있다. Widgets를 통한 동작 주입은 가능한 조합 방식이지만 의무는 아니다.
- Shared는 UI 외 api/lib/config 등 범용 기반도 포함할 수 있다. App은 routing뿐 아니라 entrypoint/providers/global configuration 등을 포함한다.
- fsd.how는 Widgets 사용을 권장하지 않는다는 자체 설명이 있다. feature-sliced.design의 Layers 문서와 별도 비교하고 이 차이를 FSD 전체의 Widgets 폐기로 단정하지 않는다.
- 같은 레이어 다른 slice 접근 금지와 slice public API를 통한 접근이 중요하다. Entity @x 명시적 교차 API는 별도 제한된 사례이지 자유 교차참조 허용이 아니다.

근거: https://fsd.how/docs/reference/layers/ , https://fsd.how/docs/reference/slices-segments/ , https://feature-sliced.design/docs/reference/layers
