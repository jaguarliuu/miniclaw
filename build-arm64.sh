#!/bin/bash
# ========================================
# MiniClaw ARM64 跨平台构建脚本
# 在 x86 机器上构建 ARM64 镜像
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
PLATFORM="linux/arm64"

echo -e "${BLUE}"
echo "  __  __ _       _  _____ _                 "
echo " |  \/  (_)     (_)/ ____| |                "
echo " | \  / |_ _ __  _| |    | | __ ___      __ "
echo " | |\/| | | '_ \| | |    | |/ _\` \ \ /\ / / "
echo " | |  | | | | | | | |____| | (_| |\ V  V /  "
echo " |_|  |_|_|_| |_|_|\_____|_|\__,_| \_/\_/   "
echo -e "${NC}"
echo -e "${GREEN}ARM64 Cross-Platform Build Script${NC}"
echo -e "${YELLOW}Target: 麒麟 V10 / ARM64 Linux${NC}"
echo ""
echo "Version: ${VERSION}"
echo "Platform: ${PLATFORM}"
echo "Output: ${OUTPUT_DIR}/${OUTPUT_FILE}"
echo ""

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed${NC}"
    exit 1
fi

# 检查 buildx
if ! docker buildx version &> /dev/null; then
    echo -e "${RED}Error: Docker buildx is not available${NC}"
    echo "Please install Docker Desktop or enable buildx plugin"
    exit 1
fi

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 创建/使用 buildx builder
BUILDER_NAME="miniclaw-arm64-builder"
if ! docker buildx inspect $BUILDER_NAME &> /dev/null; then
    echo -e "${YELLOW}Creating buildx builder for cross-platform build...${NC}"
    docker buildx create --name $BUILDER_NAME --use --platform linux/arm64,linux/amd64
    # 启用 QEMU 模拟器（用于在 x86 上构建 ARM）
    docker run --rm --privileged multiarch/qemu-user-static --reset -p yes
fi
docker buildx use $BUILDER_NAME

# Step 1: 构建后端镜像 (ARM64)
echo -e "${YELLOW}[1/4] Building backend image for ARM64...${NC}"
docker buildx build \
    --platform ${PLATFORM} \
    --tag miniclaw/backend:${VERSION}-arm64 \
    --file Dockerfile.arm64 \
    --load \
    .
echo -e "${GREEN}✓ Backend image built (ARM64)${NC}"

# Step 2: 构建前端镜像 (ARM64)
echo -e "${YELLOW}[2/4] Building frontend image for ARM64...${NC}"
docker buildx build \
    --platform ${PLATFORM} \
    --tag miniclaw/frontend:${VERSION}-arm64 \
    --file miniclaw-ui/Dockerfile.arm64 \
    --load \
    miniclaw-ui/
echo -e "${GREEN}✓ Frontend image built (ARM64)${NC}"

# Step 3: 拉取 PostgreSQL ARM64 镜像
echo -e "${YELLOW}[3/4] Pulling PostgreSQL ARM64 image...${NC}"
docker pull --platform ${PLATFORM} pgvector/pgvector:pg16
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
cp .env.example ${TEMP_DIR}/.env.example

# 创建麒麟系统部署说明
cat > ${TEMP_DIR}/README.md << 'EOF'
# MiniClaw 麒麟 V10 / ARM64 部署指南

## 系统要求

- 麒麟 V10 或其他 ARM64 Linux
- Docker 20.10+
- Docker Compose 2.0+

## 快速部署

### 1. 解压并导入镜像

```bash
tar -xzf miniclaw-arm64-*.tar.gz
cd miniclaw-deploy

# 导入所有镜像
./deploy.sh
```

### 2. 配置环境变量

```bash
cp .env.example .env
vim .env

# 必须配置：
# - LLM_ENDPOINT: LLM API 地址
# - LLM_API_KEY: API 密钥
# - LLM_MODEL: 模型名称
```

### 3. 启动服务

```bash
docker-compose up -d
```

### 4. 访问

浏览器打开 http://your-server-ip

## 麒麟系统特殊说明

### SELinux 问题

如果遇到权限问题，尝试：

```bash
# 临时关闭 SELinux
setenforce 0

# 或者给 Docker 目录添加标签
chcon -Rt svirt_sandbox_file_t /var/lib/docker
```

### 防火墙配置

```bash
# 开放 80 端口
firewall-cmd --zone=public --add-port=80/tcp --permanent
firewall-cmd --reload
```

### Docker 服务

```bash
# 启动 Docker
systemctl start docker
systemctl enable docker
```

## 常用命令

```bash
# 查看所有容器状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 查看后端日志
docker-compose logs -f backend

# 重启服务
docker-compose restart

# 停止服务
docker-compose down

# 停止并删除数据（谨慎！）
docker-compose down -v
```

## 故障排查

### 1. 后端启动失败

检查日志：
```bash
docker-compose logs backend
```

常见原因：
- LLM 配置错误
- 数据库连接超时（等待 postgres 启动）

### 2. 前端无法访问

检查 nginx 日志：
```bash
docker-compose logs frontend
```

### 3. WebSocket 连接失败

确保防火墙允许 WebSocket 连接：
```bash
# 检查 80 端口
netstat -tlnp | grep 80
```

### 4. 内存不足

ARM 设备内存可能有限，调整 JVM 参数：
```bash
# 编辑 docker-compose.yml
# 修改 JAVA_OPTS 中的 MaxRAMPercentage
```

## 数据备份

```bash
# 备份数据库
docker-compose exec postgres pg_dump -U miniclaw miniclaw > backup.sql

# 备份 workspace
docker cp miniclaw-backend:/app/workspace ./workspace-backup
```
EOF

# 创建部署脚本
cat > ${TEMP_DIR}/deploy.sh << 'EOF'
#!/bin/bash
set -e

echo "=========================================="
echo "MiniClaw ARM64 Image Loader"
echo "=========================================="
echo ""

echo "[1/3] Loading backend image..."
docker load -i backend.tar

echo "[2/3] Loading frontend image..."
docker load -i frontend.tar

echo "[3/3] Loading postgres image..."
docker load -i postgres.tar

echo ""
echo "=========================================="
echo "✓ All images loaded successfully!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "  1. cp .env.example .env"
echo "  2. vim .env  # Configure LLM credentials"
echo "  3. docker-compose up -d"
echo ""
EOF
chmod +x ${TEMP_DIR}/deploy.sh

# 打包
mkdir -p ${OUTPUT_DIR}
tar -czf ${OUTPUT_DIR}/${OUTPUT_FILE} -C ${TEMP_DIR} .

# 显示结果
SIZE=$(du -h ${OUTPUT_DIR}/${OUTPUT_FILE} | cut -f1)
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ ARM64 Build completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Output file: ${BLUE}${OUTPUT_DIR}/${OUTPUT_FILE}${NC}"
echo -e "Size: ${BLUE}${SIZE}${NC}"
echo -e "Architecture: ${BLUE}ARM64 (aarch64)${NC}"
echo ""
echo -e "${YELLOW}Deploy on 麒麟 V10:${NC}"
echo "  # 复制文件到麒麟服务器"
echo "  scp ${OUTPUT_FILE} user@kylin-server:~/"
echo ""
echo "  # 在麒麟服务器上执行"
echo "  tar -xzf ${OUTPUT_FILE}"
echo "  ./deploy.sh"
echo "  cp .env.example .env && vim .env"
echo "  docker-compose up -d"
echo ""
