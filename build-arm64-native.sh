#!/bin/bash
# ========================================
# MiniClaw ARM64 原生构建脚本（Mac M1/M2/M3 专用）
# 不需要 buildx 交叉编译
# ========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 参数
VERSION=${1:-latest}
OUTPUT_DIR=${2:-.}
OUTPUT_FILE="miniclaw-arm64-${VERSION}.tar.gz"

echo -e "${BLUE}"
echo "  __  __ _       _  _____ _                 "
echo " |  \/  (_)     (_)/ ____| |                "
echo " | \  / |_ _ __  _| |    | | __ ___      __ "
echo " | |\/| | | '_ \| | |    | |/ _\` \ \ /\ / / "
echo " | |  | | | | | | | |____| | (_| |\ V  V /  "
echo " |_|  |_|_|_| |_|_|\_____|_|\__,_| \_/\_/   "
echo -e "${NC}"
echo -e "${GREEN}ARM64 Native Build Script (Mac M1/M2/M3)${NC}"
echo ""
echo "Version: ${VERSION}"
echo "Output: ${OUTPUT_DIR}/${OUTPUT_FILE}"
echo ""

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed${NC}"
    exit 1
fi

# 检查架构（应该是 arm64）
ARCH=$(uname -m)
echo -e "${YELLOW}Detected architecture: ${ARCH}${NC}"
if [[ "$ARCH" != "arm64" ]]; then
    echo -e "${YELLOW}Warning: You are not on ARM64, consider using build-arm64.sh for cross-compilation${NC}"
fi

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Step 1: 构建后端镜像
echo -e "${YELLOW}[1/4] Building backend image (ARM64)...${NC}"
docker build \
    --tag miniclaw/backend:${VERSION}-arm64 \
    --file Dockerfile.arm64 \
    .
echo -e "${GREEN}✓ Backend image built (ARM64)${NC}"

# Step 2: 构建前端镜像
echo -e "${YELLOW}[2/4] Building frontend image (ARM64)...${NC}"
docker build \
    --tag miniclaw/frontend:${VERSION}-arm64 \
    --file miniclaw-ui/Dockerfile.arm64 \
    miniclaw-ui/
echo -e "${GREEN}✓ Frontend image built (ARM64)${NC}"

# Step 3: 拉取 PostgreSQL ARM64 镜像
echo -e "${YELLOW}[3/4] Pulling PostgreSQL ARM64 image...${NC}"
docker pull --platform linux/arm64 pgvector/pgvector:pg16
echo -e "${GREEN}✓ PostgreSQL image ready (ARM64)${NC}"

# Step 4: 打包所有镜像
echo -e "${YELLOW}[4/4] Exporting images to ${OUTPUT_FILE}...${NC}"

# 创建临时目录
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# 导出镜像
echo "  Exporting backend..."
docker save miniclaw/backend:${VERSION}-arm64 -o ${TEMP_DIR}/backend.tar
echo "  Exporting frontend..."
docker save miniclaw/frontend:${VERSION}-arm64 -o ${TEMP_DIR}/frontend.tar
echo "  Exporting postgres..."
docker save pgvector/pgvector:pg16 -o ${TEMP_DIR}/postgres.tar

# 复制部署文件
cp docker-compose.arm64.yml ${TEMP_DIR}/docker-compose.yml
cp .env.example ${TEMP_DIR}/.env.example 2>/dev/null || cat > ${TEMP_DIR}/.env.example << 'EOF'
# MiniClaw 环境配置

# LLM 配置（必填）
LLM_ENDPOINT=https://api.deepseek.com
LLM_API_KEY=sk-xxx
LLM_MODEL=deepseek-chat

# 数据库配置（可选，使用默认值）
POSTGRES_USER=miniclaw
POSTGRES_PASSWORD=miniclaw
POSTGRES_DB=miniclaw

# 端口配置
PORT=80
EOF

# 创建部署说明
cat > ${TEMP_DIR}/README.md << 'EOF'
# MiniClaw ARM64 部署指南

## 快速开始

### 1. 导入镜像

```bash
# 解压
tar -xzf miniclaw-arm64-*.tar.gz
cd miniclaw-deploy

# 导入所有镜像
docker load -i backend.tar
docker load -i frontend.tar
docker load -i postgres.tar
```

### 2. 配置环境变量

```bash
cp .env.example .env
vim .env  # 修改 LLM_ENDPOINT, LLM_API_KEY, LLM_MODEL
```

### 3. 启动服务

```bash
docker-compose up -d
```

### 4. 访问

打开浏览器访问 http://your-server-ip

## 常用命令

```bash
# 查看日志
docker-compose logs -f

# 查看后端日志
docker-compose logs -f backend

# 重启服务
docker-compose restart

# 停止服务
docker-compose down

# 停止并删除数据
docker-compose down -v
```

## 数据目录

- PostgreSQL 数据: Docker volume `miniclaw_postgres_data`
- Workspace 目录: Docker volume `miniclaw_workspace_data`

## 故障排查

1. 后端启动失败：检查 LLM 配置是否正确
2. 数据库连接失败：等待 postgres 健康检查通过
3. 前端无法连接：检查 WebSocket 代理配置
EOF

# 创建快速部署脚本
cat > ${TEMP_DIR}/deploy.sh << 'EOF'
#!/bin/bash
set -e

echo "Loading Docker images..."
docker load -i backend.tar
docker load -i frontend.tar
docker load -i postgres.tar

echo "Images loaded successfully!"
echo ""
echo "Next steps:"
echo "  1. cp .env.example .env"
echo "  2. Edit .env with your LLM credentials"
echo "  3. docker-compose up -d"
EOF
chmod +x ${TEMP_DIR}/deploy.sh

# 打包
mkdir -p ${OUTPUT_DIR}
tar -czf ${OUTPUT_DIR}/${OUTPUT_FILE} -C ${TEMP_DIR} .

# 显示结果
SIZE=$(du -h ${OUTPUT_DIR}/${OUTPUT_FILE} | cut -f1)
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ Build completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Output file: ${BLUE}${OUTPUT_DIR}/${OUTPUT_FILE}${NC}"
echo -e "Size: ${BLUE}${SIZE}${NC}"
echo -e "Architecture: ${BLUE}ARM64 (aarch64)${NC}"
echo ""
echo -e "${YELLOW}Deploy on target machine:${NC}"
echo "  tar -xzf ${OUTPUT_FILE}"
echo "  cd miniclaw-deploy"
echo "  ./deploy.sh"
echo "  cp .env.example .env && vim .env"
echo "  docker-compose up -d"
echo ""
