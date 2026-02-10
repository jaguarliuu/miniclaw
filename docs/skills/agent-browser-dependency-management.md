# agent-browser Skill 依赖管理实现

本文档说明了 agent-browser skill 的分层依赖管理策略实现。

## 架构设计原则

**核心理念：** 不内置依赖，通过智能检测 + 自动引导的方式管理外部工具依赖

**优势：**
- ✅ 保持 miniclaw 核心轻量（不包含 200-300MB 浏览器二进制）
- ✅ 跨平台兼容（Windows/macOS/Linux 各自管理依赖）
- ✅ 易于更新（用户独立更新 agent-browser）
- ✅ 灵活性高（支持用户已有的安装）
- ✅ 清晰的错误提示（自动检测并引导安装）

---

## 四层实现策略

### 🔹 第一层：Skill Gating（自动检测）

**文件：** `.miniclaw/skills/agent-browser/SKILL.md`

**实现：** 在 frontmatter 中添加 `requires` 配置

```yaml
---
name: agent-browser
description: ...
metadata:
  miniclaw:
    requires:
      anyBins: ["agent-browser", "agent-browser.cmd"]
---
```

**工作原理：**
1. `SkillRegistry` 启动时调用 `SkillGatingService.evaluate(requires)`
2. `SkillGatingService` 检查 `anyBins` 列表中的二进制是否在 PATH 中
3. 使用 `which` (Unix) 或 `where` (Windows) 命令检查
4. 如果任一二进制存在 → `available = true`
5. 如果都不存在 → `available = false`, `unsatisfiedAnyBins = [...]`

**效果：**
- Skill 自动标记为可用/不可用
- 用户看到清晰的错误提示：`Missing required binary: agent-browser`

### 🔹 第二层：文档引导（降低门槛）

**文件：** `.miniclaw/skills/agent-browser/SKILL.md`

**实现：** 在文档开头添加安装说明

```markdown
## Installation Required

This skill requires `agent-browser` to be installed...

### Quick Install

**Option 1: npm (Recommended)**
```bash
npm install -g @agent-tools/browser
```

**Option 2: Use the installation script**
```bash
bash scripts/install-agent-browser.sh
```
```

**效果：**
- 用户首次看到 skill 就知道如何安装依赖
- 提供多种安装方式（npm、脚本、手动）
- 包含验证步骤（`agent-browser --version`）

### 🔹 第三层：自动化脚本（便捷安装）

**文件：**
- `scripts/install-agent-browser.sh` - macOS/Linux
- `scripts/install-agent-browser.ps1` - Windows
- `scripts/README.md` - 脚本说明
- `scripts/test-installation.sh` - 测试验证

**实现逻辑：**

```
1. 检查是否已安装 → 显示版本并退出
   ↓
2. 尝试 npm (推荐) → npm install -g @agent-tools/browser
   ↓
3. 尝试平台特定包管理器：
   - macOS: Homebrew
   - Windows: Chocolatey
   ↓
4. 都失败 → 显示手动安装指南
```

**特性：**
- ✅ 智能检测包管理器
- ✅ 自动选择最佳安装方式
- ✅ 友好的错误提示和后备方案
- ✅ 跨平台支持（Bash + PowerShell）
- ✅ 幂等性（多次运行安全）

### 🔹 第四层：清晰错误提示（运行时保障）

**当前实现：** 通过 SkillRegistry 的 unavailable 机制

**未来增强：** 可在工具调用失败时提供更详细的引导

```java
// 伪代码示意
if (commandNotFound("agent-browser")) {
    String installGuide =
        "agent-browser not found. Install with:\n" +
        "  npm install -g @agent-tools/browser\n" +
        "Or run: bash .miniclaw/skills/agent-browser/scripts/install-agent-browser.sh";
    throw new ToolExecutionException(installGuide);
}
```

---

## 使用流程

### 场景 1：首次使用（未安装）

```
1. 用户启动 miniclaw
   ↓
2. SkillRegistry 扫描 agent-browser skill
   ↓
3. SkillGatingService 检测 agent-browser → NOT FOUND
   ↓
4. Skill 标记为 unavailable
   ↓
5. 用户尝试使用 → 看到错误提示和安装引导
   ↓
6. 用户运行：bash scripts/install-agent-browser.sh
   ↓
7. 脚本自动安装 → npm install -g @agent-tools/browser
   ↓
8. 用户重启 miniclaw 或刷新 skill registry
   ↓
9. Skill 现在标记为 available ✅
```

### 场景 2：已安装

```
1. 用户启动 miniclaw
   ↓
2. SkillRegistry 扫描 agent-browser skill
   ↓
3. SkillGatingService 检测 agent-browser → FOUND
   ↓
4. Skill 标记为 available ✅
   ↓
5. 用户直接使用 agent-browser 功能
```

---

## 文件清单

```
.miniclaw/skills/agent-browser/
├── SKILL.md                              # 主文档（包含安装引导）
├── scripts/
│   ├── install-agent-browser.sh          # Unix 安装脚本
│   ├── install-agent-browser.ps1         # Windows 安装脚本
│   ├── test-installation.sh              # 测试验证脚本
│   └── README.md                         # 脚本使用说明
└── (其他 skill 内容...)
```

---

## 验证测试

### 测试 Gating 功能

```bash
# 测试当前安装状态
bash .miniclaw/skills/agent-browser/scripts/test-installation.sh
```

**预期输出：**
```
✅ agent-browser found: agent-browser 0.6.0
   Skill should be marked as AVAILABLE
```

### 测试安装脚本

```bash
# 模拟全新安装（需先卸载 agent-browser）
npm uninstall -g @agent-tools/browser

# 运行安装脚本
bash .miniclaw/skills/agent-browser/scripts/install-agent-browser.sh

# 验证
agent-browser --version
```

### 测试 SkillRegistry

```java
// 启动应用后检查
SkillRegistry registry = ...;
SkillEntry entry = registry.getByName("agent-browser").orElse(null);

System.out.println("Available: " + entry.isAvailable());
System.out.println("Reason: " + entry.getUnavailableReason());
```

---

## 维护指南

### 更新安装脚本

如果 agent-browser 的安装方式变化：
1. 更新 `install-agent-browser.sh` 中的包名或命令
2. 更新 `install-agent-browser.ps1` 对应逻辑
3. 更新 `SKILL.md` 中的安装说明
4. 测试所有平台

### 添加新的包管理器支持

例如添加 `apt` 支持：

```bash
# 在 install-agent-browser.sh 中添加
if command -v apt &> /dev/null; then
    echo "📦 Found apt, installing agent-browser..."
    sudo apt install agent-browser
    exit 0
fi
```

### 监控依赖变化

- 关注 agent-browser 项目的 releases
- 检查是否有新的安装方式
- 更新文档中的 GitHub 链接

---

## 扩展其他 Skills

这个分层策略可以应用于其他需要外部依赖的 skills：

### 示例：Python 相关 skill

```yaml
---
name: data-analysis
description: ...
metadata:
  miniclaw:
    requires:
      bins: ["python3", "jupyter"]
      env: ["PYTHON_PATH"]
---
```

### 示例：Docker 相关 skill

```yaml
---
name: container-deploy
description: ...
metadata:
  miniclaw:
    requires:
      bins: ["docker"]
      anyBins: ["docker-compose", "docker compose"]
---
```

---

## 与原有系统的集成

### SkillGatingService 现有功能

已支持的检查类型：
- ✅ `env` - 环境变量
- ✅ `bins` - 必需二进制（全部必须存在）
- ✅ `anyBins` - 可选二进制（任一存在即可）
- ✅ `config` - Spring 配置项
- ✅ `os` - 操作系统限制

### 缓存机制

`SkillGatingService` 使用 `ConcurrentHashMap` 缓存二进制检查结果：
```java
private final Map<String, Boolean> binExistsCache = new ConcurrentHashMap<>();
```

避免重复执行 `which`/`where` 命令。

---

## 总结

✅ **已实现的四层策略：**

1. **Skill Gating** - 自动检测 agent-browser 是否可用
2. **文档引导** - 清晰的安装说明和验证步骤
3. **自动化脚本** - 智能安装脚本（Unix + Windows）
4. **运行时提示** - 通过 SkillRegistry 的 unavailable 机制

✅ **架构优势：**

- 核心系统保持轻量
- 用户按需安装依赖
- 自动检测和清晰提示
- 易于维护和扩展

✅ **用户体验：**

- 首次看到 skill 就知道如何安装
- 一键安装脚本简化操作
- 自动检测避免手动配置
- 清晰的错误提示和解决方案

这个实现完全符合现代软件工程的最佳实践，类似于 VS Code 扩展、Docker 镜像、npm 包等生态系统的依赖管理方式。
