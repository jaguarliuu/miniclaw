#!/bin/bash
# ========================================
# MiniClaw ARM64 离线包构建脚本（M1/ARM64 -> 内网 ARM64 部署）
# 重点：不使用 buildx；构建时 --pull=false；打包包含所有 base 镜像
# ========================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

VERSION=${1:-latest}
OUTPUT_DIR=${2:-.}
OUTPUT_FILE="miniclaw-arm64-${VERSION}.tar.gz"
PLATFORM="linux/arm64"

BACKEND_IMAGE="miniclaw/backend:${VERSION}-arm64"
FRONTEND_IMAGE="miniclaw/frontend:${VERSION}-arm64"

# 后端 Dockerfile 相关 base
BASE_MAVEN_IMAGE="maven:3.9-eclipse-temurin-21-alpine"
BASE_JRE_IMAGE="eclipse-temurin:21-jre-alpine"

# 前端 Dockerfile 相关 base
BASE_NODE_IMAGE="node:20-alpine"
BASE_NGINX_IMAGE="nginx:alpine"

# 运行依赖
POSTGRES_IMAGE="pgvector/pgvector:pg16"

echo -e "${GREEN}MiniClaw ARM64 Offline Package Builder${NC}"
echo -e "${YELLOW}Platform: ${PLATFORM}${NC}"
echo "Version: ${VERSION}"
echo "Output: ${OUTPUT_DIR}/${OUTPUT_FILE}"
echo ""

if ! command -v docker &>/dev/null; then
  echo -e "${RED}Error: Docker is not installed${NC}"
  exit 1
fi

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# -------- 工具函数：检查镜像是否存在 --------
require_image() {
  local img="$1"
  if ! docker image inspect "$img" >/dev/null 2>&1; then
    echo -e "${RED}Error: required image not found locally: ${img}${NC}"
    echo -e "${YELLOW}Hint: please pull it first (arm64): docker pull --platform ${PLATFORM} ${img}${NC}"
    exit 1
  fi
}

echo -e "${YELLOW}[0/5] Checking required base/runtime images are present locally...${NC}"

# 你说你已经手动拉好了，所以这里不再 pull，只做存在性检查
require_image "${BASE_MAVEN_IMAGE}"
require_image "${BASE_JRE_IMAGE}"
require_image "${BASE_NODE_IMAGE}"
require_image "${BASE_NGINX_IMAGE}"
require_image "${POSTGRES_IMAGE}"

echo -e "${GREEN}✓ All required images exist locally${NC}"
echo ""

# -------- Step 1: 构建后端（不联网） --------
echo -e "${YELLOW}[1/5] Building backend image (linux/arm64, --pull=false)...${NC}"
DOCKER_BUILDKIT=1 docker build \
  --platform "${PLATFORM}" \
  --pull=false \
  --tag "${BACKEND_IMAGE}" \
  --file Dockerfile.arm64 \
  .
echo -e "${GREEN}✓ Backend built: ${BACKEND_IMAGE}${NC}"
echo ""

# -------- Step 2: 构建前端（不联网） --------
echo -e "${YELLOW}[2/5] Building frontend image (linux/arm64, --pull=false)...${NC}"
DOCKER_BUILDKIT=1 docker build \
  --platform "${PLATFORM}" \
  --pull=false \
  --tag "${FRONTEND_IMAGE}" \
  --file miniclaw-ui/Dockerfile.arm64 \
  miniclaw-ui/
echo -e "${GREEN}✓ Frontend built: ${FRONTEND_IMAGE}${NC}"
echo ""

# -------- Step 3: 架构自检 --------
echo -e "${YELLOW}[3/5] Verifying image architectures...${NC}"
echo "  ${BACKEND_IMAGE} -> $(docker image inspect "${BACKEND_IMAGE}" --format '{{.Os}}/{{.Architecture}}')"
echo "  ${FRONTEND_IMAGE} -> $(docker image inspect "${FRONTEND_IMAGE}" --format '{{.Os}}/{{.Architecture}}')"
echo "  ${POSTGRES_IMAGE} -> $(docker image inspect "${POSTGRES_IMAGE}" --format '{{.Os}}/{{.Architecture}}')"
echo "  ${BASE_MAVEN_IMAGE} -> $(docker image inspect "${BASE_MAVEN_IMAGE}" --format '{{.Os}}/{{.Architecture}}')"
echo "  ${BASE_JRE_IMAGE} -> $(docker image inspect "${BASE_JRE_IMAGE}" --format '{{.Os}}/{{.Architecture}}')"
echo "  ${BASE_NODE_IMAGE} -> $(docker image inspect "${BASE_NODE_IMAGE}" --format '{{.Os}}/{{.Architecture}}')"
echo "  ${BASE_NGINX_IMAGE} -> $(docker image inspect "${BASE_NGINX_IMAGE}" --format '{{.Os}}/{{.Architecture}}')"
echo -e "${GREEN}✓ Architecture check done${NC}"
echo ""

# -------- Step 4: 组装离线包目录 --------
echo -e "${YELLOW}[4/5] Exporting offline package directory...${NC}"

TEMP_DIR="$(mktemp -d)"
trap "rm -rf '${TEMP_DIR}'" EXIT

# 导出镜像（包含 base 镜像，确保完全离线）
echo "  Exporting base images..."
docker save "${BASE_MAVEN_IMAGE}" -o "${TEMP_DIR}/base-maven.tar"
docker save "${BASE_JRE_IMAGE}" -o "${TEMP_DIR}/base-jre.tar"
docker save "${BASE_NODE_IMAGE}" -o "${TEMP_DIR}/base-node.tar"
docker save "${BASE_NGINX_IMAGE}" -o "${TEMP_DIR}/base-nginx.tar"

echo "  Exporting runtime images..."
docker save "${POSTGRES_IMAGE}" -o "${TEMP_DIR}/postgres.tar"

echo "  Exporting app images..."
docker save "${BACKEND_IMAGE}" -o "${TEMP_DIR}/backend.tar"
docker save "${FRONTEND_IMAGE}" -o "${TEMP_DIR}/frontend.tar"

# 复制部署编排文件
if [[ -f "docker-compose.arm64.yml" ]]; then
  cp docker-compose.arm64.yml "${TEMP_DIR}/docker-compose.yml"
else
  echo -e "${RED}Error: docker-compose.arm64.yml not found in project root${NC}"
  exit 1
fi

# env 模板（可选）
if [[ -f ".env.example" ]]; then
  cp .env.example "${TEMP_DIR}/.env.example"
fi

# README
cat > "${TEMP_DIR}/README.md" << 'EOF'
MiniClaw ARM64 离线部署包

1) 解压
tar -xzf miniclaw-arm64-*.tar.gz
cd miniclaw-deploy

2) 导入镜像
./deploy.sh

3) 配置环境变量
cp .env.example .env
vim .env

4) 启动
docker compose up -d
（老版本：docker-compose up -d）
EOF

# deploy.sh（兼容 docker compose / docker-compose）
cat > "${TEMP_DIR}/deploy.sh" << 'EOF'
#!/bin/bash
set -euo pipefail

echo "=========================================="
echo "MiniClaw ARM64 Offline Image Loader"
echo "=========================================="
echo ""

compose_cmd() {
  if command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
  else
    echo "docker compose"
  fi
}

echo "[1/6] Loading base images..."
docker load -i base-maven.tar
docker load -i base-jre.tar
docker load -i base-node.tar
docker load -i base-nginx.tar

echo "[2/6] Loading postgres image..."
docker load -i postgres.tar

echo "[3/6] Loading backend image..."
docker load -i backend.tar

echo "[4/6] Loading frontend image..."
docker load -i frontend.tar

echo ""
echo "=========================================="
echo "✓ All images loaded successfully!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "  1) cp .env.example .env"
echo "  2) vim .env  # Configure LLM credentials"
echo "  3) $(compose_cmd) up -d"
echo ""
EOF
chmod +x "${TEMP_DIR}/deploy.sh"

# -------- Step 5: 打包成 tar.gz（确保解压后有 miniclaw-deploy 目录） --------
echo -e "${YELLOW}[5/5] Creating ${OUTPUT_FILE}...${NC}"

DEPLOY_DIR_NAME="miniclaw-deploy"
FINAL_DIR="${TEMP_DIR}_final"
mkdir -p "${FINAL_DIR}/${DEPLOY_DIR_NAME}"
cp -a "${TEMP_DIR}/." "${FINAL_DIR}/${DEPLOY_DIR_NAME}/"

mkdir -p "${OUTPUT_DIR}"
tar -czf "${OUTPUT_DIR}/${OUTPUT_FILE}" -C "${FINAL_DIR}" "${DEPLOY_DIR_NAME}"

SIZE=$(du -h "${OUTPUT_DIR}/${OUTPUT_FILE}" | awk '{print $1}')
echo ""
echo -e "${GREEN}✓ Offline package built: ${OUTPUT_DIR}/${OUTPUT_FILE}${NC}"
echo -e "${GREEN}Size: ${SIZE}${NC}"
echo ""