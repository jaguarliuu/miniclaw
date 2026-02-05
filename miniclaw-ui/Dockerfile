# ========================================
# MiniClaw Frontend Dockerfile
# Multi-stage build: Node + Nginx
# ========================================

# Stage 1: Build
FROM node:20-alpine AS builder

WORKDIR /app

# 先复制 package.json，利用 Docker 缓存
COPY package*.json ./
RUN npm ci

# 复制源码并构建
COPY . .
RUN npm run build

# Stage 2: Runtime (Nginx)
FROM nginx:alpine

LABEL maintainer="MiniClaw"
LABEL description="MiniClaw Frontend"

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
