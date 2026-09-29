FROM node:24-bookworm-slim@sha256:0e0ff40c39bc087845bfb27465a0df4ea419520094bc35842ff83dd8cbe6f9b6 AS build
RUN apt-get update && apt-get install -y --no-install-recommends python3 make g++ && rm -rf /var/lib/apt/lists/*
WORKDIR /app/apps/dearby-api
COPY apps/dearby-api/package*.json ./
RUN npm ci
COPY apps/dearby-api/tsconfig*.json ./
COPY apps/dearby-api/prisma.config.ts ./
COPY apps/dearby-api/prisma ./prisma
COPY apps/dearby-api/src ./src
COPY apps/dearby-api/test ./test
COPY apps/dearby-api/migrations ./migrations
COPY shared/data/catalog-snapshot-2026-09-24.json /app/shared/data/catalog-snapshot-2026-09-24.json
RUN npm run lint && npm run typecheck && npm run build
RUN npm prune --omit=dev --omit=optional

FROM node:24-bookworm-slim@sha256:0e0ff40c39bc087845bfb27465a0df4ea419520094bc35842ff83dd8cbe6f9b6
ENV NODE_ENV=production HOST=0.0.0.0 PORT=3000 DATABASE_CA_FILE=/run/secrets/database-ca.crt
WORKDIR /app/apps/dearby-api
COPY --from=build /app/apps/dearby-api/package*.json ./
COPY --from=build /app/apps/dearby-api/node_modules ./node_modules
COPY --from=build /app/apps/dearby-api/dist ./dist
COPY --from=build /app/apps/dearby-api/migrations ./migrations
COPY --from=build /app/shared/data /app/shared/data
RUN mkdir /data && chown node:node /data && chmod 700 /data
USER node
CMD ["node", "dist/server.js"]
