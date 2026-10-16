#!/bin/sh
# 모듈 테스트는 모두 같은 테스트 DB(postgres-test)를 비우고 다시 쓰므로 한 번에 하나만 돈다.
# 여러 세션이 같은 checkout에서 작업할 때 이 스크립트로 gradle을 실행한다. 예: scripts/test-locked.sh test
set -e
LOCK="${TMPDIR:-/tmp}/dearby-api-gradle.lock"
until mkdir "$LOCK" 2>/dev/null; do sleep 5; done
trap 'rmdir "$LOCK"' EXIT INT TERM
cd "$(dirname "$0")/.."
./gradlew "$@"
