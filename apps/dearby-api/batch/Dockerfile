# 빌드 컨텍스트는 apps/dearby-api(멀티모듈 루트). compose.prod.yaml: build.context=., dockerfile=batch/Dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY core core
COPY batch batch
RUN ./gradlew --no-daemon :batch:bootJar && mv batch/build/libs/*.jar app.jar

# 활동 벡터 모델(native-v1.md "활동 벡터"): 고정 판(revision)·체크섬의 safetensors를 받아 ONNX로 바꾼다(외부 API 없이 batch JVM이 ONNX Runtime으로 돌린다).
# EMBEDDING_VARIANT=int8(기본, ONNX Runtime에서 fp32보다 빠름) 또는 fp32. 바꾸면 application.yaml의 dearby.embedding.model-id도 함께 바꾼다
FROM python:3.12-slim AS embedding-model
ARG EMBEDDING_REPO=nlpai-lab/KURE-v1
ARG EMBEDDING_REVISION=8b418a58414668e75532ed045c22d9ca018ae2b2
ARG EMBEDDING_WEIGHTS_SHA256=c18156e80caf8ff45eb84a24a853130c3bca03087ccb41b051f86e7556bae02c
ARG EMBEDDING_VARIANT=int8
RUN pip install --no-cache-dir torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir transformers==4.46.3 optimum==1.23.3 onnx==1.17.0 onnxruntime==1.20.1 huggingface_hub==0.26.2 sentencepiece==0.2.0
RUN python -c "from huggingface_hub import snapshot_download; snapshot_download('${EMBEDDING_REPO}', revision='${EMBEDDING_REVISION}', local_dir='/src', allow_patterns=['*.json', 'model.safetensors'])" \
    && echo "${EMBEDDING_WEIGHTS_SHA256}  /src/model.safetensors" | sha256sum -c -
RUN optimum-cli export onnx --model /src --task feature-extraction --library-name transformers --opset 17 --framework pt /fp32 \
    && if [ "${EMBEDDING_VARIANT}" = "int8" ]; then \
         case "$(uname -m)" in aarch64|arm64) arch=--arm64 ;; *) arch=--avx2 ;; esac; \
         optimum-cli onnxruntime quantize --onnx_model /fp32 $arch -o /int8 \
         && mkdir /out && cp /int8/model_quantized.onnx /out/model.onnx && cp /fp32/tokenizer.json /out/; \
       else mv /fp32 /out; fi \
    && ls -la /out

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=embedding-model /out /app/models/kure-v1
# 브라우저 렌더링 수집(admin-v1.md "브라우저 렌더링"): Playwright가 쓰는 Chromium을 이미지에 넣는다. 모든 사용자가 읽을 수 있는 경로에 둔다
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
# 실행 시 Playwright가 브라우저를 다시 내려받으려 하지 않게 한다(2026-10-19: 첫 렌더링에서 install 프로세스를 기다리며 멈추던 문제)
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
# 수집 에이전트를 돌리는 Claude Code CLI(옵션을 확인한 2.1.292로 고정, 올릴 때 --max-turns·--system-prompt-file·--mcp-config·--max-budget-usd를 다시 확인). Node.js는 공식 node 이미지에서 가져온다
COPY --from=node:24-bookworm-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=node:24-bookworm-slim /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && npm install -g @anthropic-ai/claude-code@2.1.292 \
    && npm cache clean --force \
    && useradd --system --uid 10001 --create-home app
COPY --from=build /src/app.jar app.jar
# Playwright CLI는 부트 jar 안의 playwright 라이브러리로 실행한다(PropertiesLauncher). --with-deps가 Chromium 런타임 패키지를 apt로 설치한다
RUN java -cp app.jar -Dloader.main=com.microsoft.playwright.CLI org.springframework.boot.loader.launch.PropertiesLauncher install --with-deps chromium \
    && chmod -R a+rX /ms-playwright \
    && rm -rf /var/lib/apt/lists/*
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
