# Dearby API

NestJS CLI 12.0.0으로 초기화한 TypeScript API 서버입니다.
NestJS 12, Express, ESM, Vitest, oxlint와 npm을 사용합니다.

## 설치와 실행

Node.js 26.5.0 / npm 12.0.2에서 검증합니다.
모노레포 루트에서 실행합니다.

```sh
cd apps/dearby-api
npm ci
npm run start:dev
```

기본 주소는 `http://localhost:3000`이며, `GET /`는 `Hello World!`를 반환합니다.
포트는 환경 변수로 변경할 수 있습니다.

```sh
PORT=3001 npm run start:dev
```

현재는 CLI의 기본 컨트롤러·서비스만 있으며, 데이터베이스·인증·앱 연동은 아직 없습니다.

## 검증

`apps/dearby-api/`에서 실행합니다.

```sh
npm run build
npm run lint
npm test
npm run test:e2e
```

빌드한 서버는 `npm run start:prod`로 실행합니다.
패키지 설치 시에는 커밋된 `package-lock.json`을 사용하는 `npm ci`를 권장합니다.

## 생성 명령

모노레포 루트에서 다음 CLI 명령을 사용하고, 모듈 방식은 ESM을 선택했습니다.

```sh
npx --yes @nestjs/cli@12.0.0 new dearby-api \
  --directory apps/dearby-api \
  --package-manager npm \
  --language TS \
  --strict \
  --skip-git \
  --no-observe
```

새 기능은 이 디렉터리에서 `npx nest generate module <name>` 등의 CLI 명령으로 추가합니다.
Git 이력은 모노레포 루트에서 함께 관리합니다.

참고: [NestJS CLI 공식 문서](https://docs.nestjs.com/cli/usages).
