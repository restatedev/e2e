# Build stage: install deps and compile TypeScript.
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json tsconfig.json ./
RUN npm install --no-audit --no-fund

COPY src ./src
RUN npx tsc

# Runtime stage: production-only deps + compiled JS.
FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production

COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

COPY --from=builder /app/dist ./dist

EXPOSE 9080
CMD ["node", "dist/index.js"]
