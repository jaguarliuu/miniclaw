# 第3.1节：开发环境准备

> **学习目标**：配置完整的开发环境，安装必要的工具
> **预计时长**：20 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [ ] 基本的命令行操作
- [ ] 知道什么是 JDK 和 Maven

**如果你不确定**：
- 命令行不熟 → 本节会提供详细步骤
- 没用过 Docker → 本节会从安装开始讲解

---

### 为什么需要这些工具？

在开发 AI Agent 之前，我们需要准备一套"武器库"：

| 工具 | 作用 | 为什么需要 |
|------|------|-----------|
| **JDK 21** | Java 开发工具包 | Spring Boot 3.4 要求 Java 21+ |
| **Maven** | 构建工具 | 管理依赖、编译、打包 |
| **Docker** | 容器运行时 | 运行 PostgreSQL、pgvector 等服务 |
| **IDEA** | 集成开发环境 | 代码编辑、调试、重构 |

---

### 第一步：安装 JDK 21

#### macOS

```bash
# 使用 SDKMAN 安装（推荐）
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.1-tem

# 验证安装
java -version
# 输出：openjdk version "21.0.1"
```

#### Windows

1. 下载 [Oracle JDK 21](https://www.oracle.com/java/technologies/downloads/#java21)
2. 运行安装程序
3. 配置环境变量 `JAVA_HOME`
4. 验证：`java -version`

#### Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install openjdk-21-jdk
java -version
```

---

### 第二步：安装 Maven

#### macOS

```bash
# 使用 Homebrew
brew install maven

# 验证
mvn -version
# 输出：Apache Maven 3.9.x
```

#### Windows

1. 下载 [Maven](https://maven.apache.org/download.cgi)
2. 解压到 `C:\Program Files\Apache\maven`
3. 添加到 PATH 环境变量
4. 验证：`mvn -version`

#### Linux

```bash
sudo apt install maven
mvn -version
```

---

### 第三步：安装 Docker

Docker 用于运行数据库等基础设施。

#### macOS

```bash
# 下载 Docker Desktop for Mac
# https://www.docker.com/products/docker-desktop

# 安装后验证
docker --version
# 输出：Docker version 24.x.x
docker compose version
# 输出：Docker Compose version v2.x.x
```

#### Windows

1. 下载 [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop)
2. 启用 WSL 2（Windows Subsystem for Linux）
3. 安装并启动 Docker Desktop
4. 验证：`docker --version`

#### Linux

```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 验证
docker --version
docker compose version
```

---

### 第四步：安装 IDEA

**IntelliJ IDEA** 是 Java 开发的首选 IDE。

#### 下载地址

- **Ultimate 版**（推荐）：https://www.jetbrains.com/idea/download/
- **Community 版**（免费）：功能够用

#### 推荐插件

安装完成后，安装以下插件：

1. **Lombok** - 支持 @Data 等注解
2. **Spring Boot Helper** - Spring 开发增强
3. **Docker** - 容器管理

---

### 验证环境

运行以下命令验证所有工具已正确安装：

```bash
# 检查 Java
java -version
# 期望：openjdk version "21.x.x"

# 检查 Maven
mvn -version
# 期望：Apache Maven 3.9.x

# 检查 Docker
docker --version
# 期望：Docker version 24.x.x

# 检查 Docker Compose
docker compose version
# 期望：Docker Compose version v2.x.x
```

---

### 常见问题

#### Q: JDK 版本不对怎么办？

```bash
# 检查所有已安装的 Java
/usr/libexec/java_home -V

# 切换到 Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

#### Q: Docker 启动很慢？

- macOS/Windows：给 Docker Desktop 分配更多内存（建议 8GB+）
- 检查是否有虚拟机冲突

#### Q: Maven 下载依赖很慢？

配置阿里云镜像，编辑 `~/.m2/settings.xml`：

```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <name>Aliyun Maven Mirror</name>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

---

### 本节小结

- 我们安装了 JDK 21、Maven、Docker、IDEA
- 验证了所有工具都能正常工作
- 下一节我们将使用 Docker Compose 编排基础设施（PostgreSQL + pgvector）

---

### 扩展阅读

- [JDK 21 新特性](https://openjdk.org/projects/jdk/21/)
- [Maven 官方文档](https://maven.apache.org/guides/)
- [Docker 入门教程](https://docs.docker.com/get-started/)
