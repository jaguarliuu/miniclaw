# Phase 3: Skills 系统实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现企业级的 Skills 系统，采用 OpenClaw/AgentSkills 兼容的架构

---

## 设计哲学

### 核心理念：Skill 是"说明书"，不是"可执行插件"

```
Skill = 教 Agent 怎么用工具的 SOP（标准操作流程）
```

- **可读性强**：像文档一样可以审计
- **可控性强**：load-time gating + token 成本可预估
- **生态兼容**：AgentSkills-compatible 格式

### 完整链路

```
┌─────────────────────────────────────────────────────────────────────┐
│  1. Discovery（发现）                                                │
│     扫描多个位置，按优先级合并                                        │
│     <workspace>/.miniclaw/skills → ~/.miniclaw/skills → bundled     │
└───────────────────────────────────┬─────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│  2. Gating（过滤）                                                   │
│     根据 requires 判定可用性：env / bins / config / os              │
│     不满足条件 → 标记为 unavailable + reason                         │
└───────────────────────────────────┬─────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│  3. Indexing（索引）                                                 │
│     构建紧凑的 XML 索引，只包含 name/description                     │
│     计算 token 成本：base_overhead + per_skill_cost                 │
└───────────────────────────────────┬─────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│  4. Injection（注入）                                                │
│     会话启动时，将索引注入 system prompt                             │
│     模型知道"有哪些技能可用"但不知道详细内容                          │
└───────────────────────────────────┬─────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│  5. Activation（激活）- Progressive Disclosure                       │
│     模型选择某个 skill → 才加载详细指令                              │
│     手动触发：/skill-name args                                       │
│     自动选择：LLM 回复 [USE_SKILL:xxx]                               │
└───────────────────────────────────┬─────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│  6. Execution（执行）                                                │
│     注入运行时 env（单次 run 生效）                                   │
│     Skill 指导工具调用，allowed-tools 做权限控制                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## SKILL.md 文件规范

### 完整格式

```yaml
---
name: code-review                           # 唯一标识（必需）
description: 代码审查，检查代码质量和问题     # 简短描述（必需，用于索引）
allowed-tools:                              # 工具白名单（可选）
  - read_file
  - memory_search
confirm-before: []                          # 覆盖 HITL 设置（可选）
metadata:
  miniclaw:
    requires:                               # 可用性条件（可选）
      env:                                  # 需要的环境变量
        - OPENAI_API_KEY
      bins:                                 # 需要的二进制程序
        - git
        - node
      anyBins:                              # 任一存在即可
        - npm
        - yarn
        - pnpm
      config:                               # 需要的配置项为 true
        - git.enabled
      os:                                   # 支持的操作系统
        - darwin
        - linux
        - win32
    primaryEnv: OPENAI_API_KEY              # 主要环境变量（UI 展示用）
    install:                                # 安装方式（可选，未来扩展）
      brew: []
      npm: []
---

# Code Review Skill

你是一个代码审查专家...

## 什么时候用
当用户说"审查代码"、"review"、"检查代码质量"时使用。

## 操作步骤
1. 读取指定文件
2. 分析代码质量
3. 输出审查报告

用户请求: $ARGUMENTS
```

### 目录结构

```
.miniclaw/
  skills/
    code-review/
      SKILL.md              # 核心：技能说明书
      scripts/              # 可选：辅助脚本
      templates/            # 可选：模板文件
    git-commit/
      SKILL.md
    daily-summary/
      SKILL.md
```

### 加载位置与优先级

```
优先级从高到低：
1. <workspace>/.miniclaw/skills/   （项目级，最高优先级）
2. ~/.miniclaw/skills/             （用户级）
3. bundled skills                   （内置，最低优先级）

同名 skill：高优先级覆盖低优先级
```

---

## 架构设计

### 核心组件

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SkillRegistry                                 │
│  职责：统一管理 skill 生命周期                                        │
│  数据：ConcurrentHashMap<String, SkillEntry>                        │
│                                                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │
│  │ SkillEntry  │  │ SkillEntry  │  │ SkillEntry  │  ...             │
│  │ - metadata  │  │ - metadata  │  │ - metadata  │                  │
│  │ - available │  │ - available │  │ - available │                  │
│  │ - reason    │  │ - reason    │  │ - reason    │                  │
│  └─────────────┘  └─────────────┘  └─────────────┘                  │
└───────────────────────────────────┬─────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ↓                           ↓                           ↓
┌───────────────┐          ┌───────────────┐          ┌───────────────┐
│ SkillDiscovery│          │ SkillGating   │          │ SkillLoader   │
│               │          │               │          │               │
│ - 扫描目录     │          │ - 检查 env    │          │ - 懒加载正文   │
│ - 解析 YAML   │          │ - 检查 bins   │          │ - 编译 $ARGS  │
│ - 监听变化     │          │ - 检查 config │          │ - 注入 env    │
└───────────────┘          │ - 检查 os     │          └───────────────┘
                           └───────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                        SkillIndexBuilder                             │
│  职责：构建 token-aware 的 skill 索引                                │
│                                                                      │
│  - 计算 token 成本                                                   │
│  - 生成紧凑 XML 索引                                                 │
│  - 支持 budget 控制                                                  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                        SkillSelector                                 │
│  职责：skill 选择与激活                                              │
│                                                                      │
│  - 手动触发：/skill-name args                                        │
│  - 自动选择：解析 LLM 的 [USE_SKILL:xxx]                             │
└─────────────────────────────────────────────────────────────────────┘
```

### 数据模型

```java
// SkillEntry：完整的 skill 条目（包含可用性状态）
SkillEntry {
    SkillMetadata metadata;     // 元数据
    boolean available;          // 是否可用
    String unavailableReason;   // 不可用原因
    long lastModified;          // 文件修改时间
    int tokenCost;              // 预估 token 成本
}

// SkillMetadata：从 YAML frontmatter 解析
SkillMetadata {
    String name;
    String description;
    List<String> allowedTools;
    List<String> confirmBefore;
    SkillRequires requires;
    Path sourcePath;
    int priority;
}

// SkillRequires：可用性条件
SkillRequires {
    List<String> env;           // 需要的环境变量
    List<String> bins;          // 需要的二进制（全部）
    List<String> anyBins;       // 需要的二进制（任一）
    List<String> config;        // 需要的配置项
    List<String> os;            // 支持的操作系统
}

// LoadedSkill：激活后的完整 skill（包含正文）
LoadedSkill {
    String name;
    String description;
    String body;                // 完整正文
    Set<String> allowedTools;
    Set<String> confirmBefore;
    Map<String, String> runtimeEnv;  // 运行时注入的环境变量
}
```

---

## 任务分解

### Task P3-01: 数据模型

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/skills/model/SkillMetadata.java`
- Create: `src/main/java/com/jaguarliu/ai/skills/model/SkillRequires.java`
- Create: `src/main/java/com/jaguarliu/ai/skills/model/SkillEntry.java`
- Create: `src/main/java/com/jaguarliu/ai/skills/model/LoadedSkill.java`

**Step 1: SkillRequires（可用性条件）**

```java
package com.jaguarliu.ai.skills.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * Skill 可用性条件
 * 对应 SKILL.md 中的 metadata.miniclaw.requires
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillRequires {
    /** 需要的环境变量（全部必须存在） */
    private List<String> env;

    /** 需要的二进制程序（全部必须存在） */
    private List<String> bins;

    /** 需要的二进制程序（任一存在即可） */
    private List<String> anyBins;

    /** 需要的配置项为 true */
    private List<String> config;

    /** 支持的操作系统：darwin / linux / win32 */
    private List<String> os;
}
```

**Step 2: SkillMetadata（元数据）**

```java
package com.jaguarliu.ai.skills.model;

import lombok.Data;
import lombok.Builder;
import java.nio.file.Path;
import java.util.List;

/**
 * Skill 元数据
 * 从 SKILL.md 的 YAML frontmatter 解析
 * 只包含"索引"需要的信息，不包含正文
 */
@Data
@Builder
public class SkillMetadata {
    /** 唯一标识（必需） */
    private String name;

    /** 简短描述（必需，用于索引和匹配） */
    private String description;

    /** 允许的工具白名单 */
    private List<String> allowedTools;

    /** 需要 HITL 确认的工具（覆盖默认配置） */
    private List<String> confirmBefore;

    /** 可用性条件 */
    private SkillRequires requires;

    /** 主要环境变量（UI 展示用） */
    private String primaryEnv;

    /** 源文件路径 */
    private Path sourcePath;

    /** 优先级：0=项目级, 1=用户级, 2=内置 */
    private int priority;
}
```

**Step 3: SkillEntry（完整条目，包含可用性状态）**

```java
package com.jaguarliu.ai.skills.model;

import lombok.Data;
import lombok.Builder;

/**
 * Skill 条目
 * 包含元数据 + 可用性状态 + 成本信息
 * 这是 SkillRegistry 中存储的完整对象
 */
@Data
@Builder
public class SkillEntry {
    /** 元数据 */
    private SkillMetadata metadata;

    /** 是否可用（gating 结果） */
    private boolean available;

    /** 不可用原因 */
    private String unavailableReason;

    /** 文件最后修改时间（用于热更新检测） */
    private long lastModified;

    /** 预估 token 成本（索引部分） */
    private int tokenCost;

    /**
     * 计算索引 token 成本
     * 公式：base_overhead + name_tokens + description_tokens
     */
    public static int calculateTokenCost(SkillMetadata metadata) {
        // 基础开销（XML 标签等）
        int baseCost = 20;
        // name 按字符数估算（中文约 2 token/字，英文约 0.25 token/字）
        int nameCost = estimateTokens(metadata.getName());
        // description 同理
        int descCost = estimateTokens(metadata.getDescription());

        return baseCost + nameCost + descCost;
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 简单估算：中文字符算 2 token，其他算 0.3 token
        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }
        return (int) (chineseCount * 2 + otherCount * 0.3);
    }
}
```

**Step 4: LoadedSkill（激活后的完整 skill）**

```java
package com.jaguarliu.ai.skills.model;

import lombok.Data;
import lombok.Builder;
import java.util.Map;
import java.util.Set;

/**
 * 已加载的 Skill
 * 包含完整正文，用于激活后的执行
 * 这是 Progressive Disclosure 的"展开"阶段
 */
@Data
@Builder
public class LoadedSkill {
    /** 唯一标识 */
    private String name;

    /** 描述 */
    private String description;

    /** 完整正文（Markdown） */
    private String body;

    /** 允许的工具 */
    private Set<String> allowedTools;

    /** 需要确认的工具 */
    private Set<String> confirmBefore;

    /** 运行时注入的环境变量（单次 run 生效） */
    private Map<String, String> runtimeEnv;
}
```

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/skills/model/
git commit -m "feat(skills): [P3-01] add skill data models"
```

---

### Task P3-02: SKILL.md 解析器

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/skills/parser/SkillParser.java`
- Create: `src/main/java/com/jaguarliu/ai/skills/parser/SkillParseResult.java`

**Step 1: SkillParseResult**

```java
package com.jaguarliu.ai.skills.parser;

import com.jaguarliu.ai.skills.model.SkillMetadata;
import lombok.Data;
import lombok.Builder;

/**
 * SKILL.md 解析结果
 */
@Data
@Builder
public class SkillParseResult {
    /** 元数据（来自 YAML frontmatter） */
    private SkillMetadata metadata;

    /** 正文（Markdown 内容） */
    private String body;

    /** 是否有效 */
    private boolean valid;

    /** 解析错误信息 */
    private String errorMessage;

    /** 文件最后修改时间 */
    private long lastModified;

    public static SkillParseResult error(String message) {
        return SkillParseResult.builder()
                .valid(false)
                .errorMessage(message)
                .build();
    }
}
```

**Step 2: SkillParser**

```java
package com.jaguarliu.ai.skills.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jaguarliu.ai.skills.model.SkillMetadata;
import com.jaguarliu.ai.skills.model.SkillRequires;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SKILL.md 解析器
 * 解析 YAML frontmatter + Markdown body
 */
@Slf4j
@Component
public class SkillParser {

    // 匹配 YAML frontmatter：--- 开头，--- 结尾
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n(.*)$",
            Pattern.DOTALL
    );

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * 解析 SKILL.md 文件
     *
     * @param skillPath SKILL.md 文件路径
     * @param priority  优先级（0=项目级, 1=用户级, 2=内置）
     */
    public SkillParseResult parse(Path skillPath, int priority) {
        try {
            if (!Files.exists(skillPath)) {
                return SkillParseResult.error("File not found: " + skillPath);
            }

            String content = Files.readString(skillPath, StandardCharsets.UTF_8);
            long lastModified = Files.getLastModifiedTime(skillPath).toMillis();

            return parse(content, skillPath, priority, lastModified);

        } catch (IOException e) {
            log.error("Failed to read skill file: {}", skillPath, e);
            return SkillParseResult.error("Failed to read file: " + e.getMessage());
        }
    }

    /**
     * 解析 SKILL.md 内容
     */
    public SkillParseResult parse(String content, Path sourcePath, int priority, long lastModified) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content.trim());

        if (!matcher.matches()) {
            return SkillParseResult.error("Invalid SKILL.md format: missing YAML frontmatter (must start and end with ---)");
        }

        String yamlPart = matcher.group(1);
        String bodyPart = matcher.group(2).trim();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> frontmatter = yamlMapper.readValue(yamlPart, Map.class);

            // 提取必需字段
            String name = getString(frontmatter, "name");
            String description = getString(frontmatter, "description");

            if (name == null || name.isBlank()) {
                return SkillParseResult.error("Missing required field: name");
            }
            if (description == null || description.isBlank()) {
                return SkillParseResult.error("Missing required field: description");
            }

            // 提取可选字段
            @SuppressWarnings("unchecked")
            List<String> allowedTools = (List<String>) frontmatter.get("allowed-tools");
            @SuppressWarnings("unchecked")
            List<String> confirmBefore = (List<String>) frontmatter.get("confirm-before");

            // 解析 metadata.miniclaw
            SkillRequires requires = parseRequires(frontmatter);
            String primaryEnv = parsePrimaryEnv(frontmatter);

            // 构建元数据
            SkillMetadata metadata = SkillMetadata.builder()
                    .name(name.trim())
                    .description(description.trim())
                    .allowedTools(allowedTools)
                    .confirmBefore(confirmBefore)
                    .requires(requires)
                    .primaryEnv(primaryEnv)
                    .sourcePath(sourcePath)
                    .priority(priority)
                    .build();

            return SkillParseResult.builder()
                    .metadata(metadata)
                    .body(bodyPart)
                    .valid(true)
                    .lastModified(lastModified)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse SKILL.md: {}", sourcePath, e);
            return SkillParseResult.error("YAML parse error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private SkillRequires parseRequires(Map<String, Object> frontmatter) {
        Map<String, Object> metadata = (Map<String, Object>) frontmatter.get("metadata");
        if (metadata == null) return null;

        Map<String, Object> miniclaw = (Map<String, Object>) metadata.get("miniclaw");
        if (miniclaw == null) return null;

        Map<String, Object> requires = (Map<String, Object>) miniclaw.get("requires");
        if (requires == null) return null;

        return SkillRequires.builder()
                .env((List<String>) requires.get("env"))
                .bins((List<String>) requires.get("bins"))
                .anyBins((List<String>) requires.get("anyBins"))
                .config((List<String>) requires.get("config"))
                .os((List<String>) requires.get("os"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private String parsePrimaryEnv(Map<String, Object> frontmatter) {
        Map<String, Object> metadata = (Map<String, Object>) frontmatter.get("metadata");
        if (metadata == null) return null;

        Map<String, Object> miniclaw = (Map<String, Object>) metadata.get("miniclaw");
        if (miniclaw == null) return null;

        return (String) miniclaw.get("primaryEnv");
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
```

**Step 3: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/skills/parser/
git commit -m "feat(skills): [P3-02] add SKILL.md parser"
```

---

### Task P3-03: Gating 服务（可用性检查）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/skills/gating/SkillGatingService.java`
- Create: `src/main/java/com/jaguarliu/ai/skills/gating/GatingResult.java`

**Step 1: GatingResult**

```java
package com.jaguarliu.ai.skills.gating;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.ArrayList;

/**
 * Gating 检查结果
 */
@Data
@Builder
public class GatingResult {
    /** 是否可用 */
    private boolean available;

    /** 不可用原因列表 */
    private List<String> reasons;

    public static GatingResult ok() {
        return GatingResult.builder()
                .available(true)
                .reasons(List.of())
                .build();
    }

    public static GatingResult fail(List<String> reasons) {
        return GatingResult.builder()
                .available(false)
                .reasons(reasons)
                .build();
    }

    /**
     * 获取格式化的不可用原因
     */
    public String getFormattedReason() {
        if (reasons == null || reasons.isEmpty()) {
            return null;
        }
        return String.join("; ", reasons);
    }
}
```

**Step 2: SkillGatingService**

```java
package com.jaguarliu.ai.skills.gating;

import com.jaguarliu.ai.skills.model.SkillRequires;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Skill 可用性检查服务
 *
 * 在 load-time 检查 skill 是否满足运行条件：
 * - env: 环境变量是否存在
 * - bins: 二进制程序是否在 PATH 中
 * - anyBins: 任一二进制存在即可
 * - config: 配置项是否为 true
 * - os: 当前操作系统是否支持
 */
@Slf4j
@Service
public class SkillGatingService {

    private final Environment environment;
    private final String currentOs;

    public SkillGatingService(Environment environment) {
        this.environment = environment;
        this.currentOs = detectCurrentOs();
        log.info("SkillGatingService initialized. Current OS: {}", currentOs);
    }

    /**
     * 检查 skill 可用性
     */
    public GatingResult check(SkillRequires requires) {
        if (requires == null) {
            return GatingResult.ok();
        }

        List<String> failReasons = new ArrayList<>();

        // 1. 检查环境变量
        checkEnvVars(requires.getEnv(), failReasons);

        // 2. 检查二进制程序（全部必须存在）
        checkBinaries(requires.getBins(), failReasons, true);

        // 3. 检查二进制程序（任一存在即可）
        checkAnyBinaries(requires.getAnyBins(), failReasons);

        // 4. 检查配置项
        checkConfigs(requires.getConfig(), failReasons);

        // 5. 检查操作系统
        checkOs(requires.getOs(), failReasons);

        if (failReasons.isEmpty()) {
            return GatingResult.ok();
        } else {
            return GatingResult.fail(failReasons);
        }
    }

    private void checkEnvVars(List<String> envVars, List<String> failReasons) {
        if (envVars == null || envVars.isEmpty()) return;

        for (String envVar : envVars) {
            String value = System.getenv(envVar);
            if (value == null || value.isBlank()) {
                failReasons.add("Missing env: " + envVar);
            }
        }
    }

    private void checkBinaries(List<String> bins, List<String> failReasons, boolean allRequired) {
        if (bins == null || bins.isEmpty()) return;

        for (String bin : bins) {
            if (!isBinaryInPath(bin)) {
                failReasons.add("Missing binary: " + bin);
            }
        }
    }

    private void checkAnyBinaries(List<String> anyBins, List<String> failReasons) {
        if (anyBins == null || anyBins.isEmpty()) return;

        boolean anyFound = false;
        for (String bin : anyBins) {
            if (isBinaryInPath(bin)) {
                anyFound = true;
                break;
            }
        }

        if (!anyFound) {
            failReasons.add("Missing any of: " + String.join(" / ", anyBins));
        }
    }

    private void checkConfigs(List<String> configs, List<String> failReasons) {
        if (configs == null || configs.isEmpty()) return;

        for (String configKey : configs) {
            String value = environment.getProperty(configKey);
            if (!"true".equalsIgnoreCase(value)) {
                failReasons.add("Config not enabled: " + configKey);
            }
        }
    }

    private void checkOs(List<String> supportedOs, List<String> failReasons) {
        if (supportedOs == null || supportedOs.isEmpty()) return;

        if (!supportedOs.contains(currentOs)) {
            failReasons.add("Unsupported OS: " + currentOs + " (requires: " + supportedOs + ")");
        }
    }

    /**
     * 检查二进制是否在 PATH 中
     */
    private boolean isBinaryInPath(String binary) {
        // Windows 需要加 .exe 后缀
        String executableName = isWindows() ? binary + ".exe" : binary;

        // 检查 PATH 环境变量
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return false;

        String pathSeparator = File.pathSeparator;
        for (String dir : pathEnv.split(pathSeparator)) {
            File file = new File(dir, executableName);
            if (file.exists() && file.canExecute()) {
                return true;
            }
            // Windows 上也检查不带 .exe 的
            if (isWindows()) {
                File fileWithoutExe = new File(dir, binary);
                if (fileWithoutExe.exists() && fileWithoutExe.canExecute()) {
                    return true;
                }
            }
        }

        // 备选方案：尝试执行 which/where 命令
        return checkBinaryWithCommand(binary);
    }

    private boolean checkBinaryWithCommand(String binary) {
        try {
            String command = isWindows() ? "where " + binary : "which " + binary;
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private String detectCurrentOs() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) return "win32";
        if (osName.contains("mac") || osName.contains("darwin")) return "darwin";
        if (osName.contains("linux")) return "linux";
        return osName;
    }

    private boolean isWindows() {
        return "win32".equals(currentOs);
    }
}
```

**Step 3: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/skills/gating/
git commit -m "feat(skills): [P3-03] add skill gating service"
```

---

### Task P3-04: Skill 注册表

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/skills/SkillRegistry.java`
- Create: `src/main/java/com/jaguarliu/ai/skills/SkillsProperties.java`

**Step 1: SkillsProperties**

```java
package com.jaguarliu.ai.skills;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "skills")
public class SkillsProperties {
    /**
     * Skill 扫描路径（按优先级排序）
     * 索引越小优先级越高
     */
    private List<String> paths = List.of(
            ".miniclaw/skills",      // 项目级（最高）
            "~/.miniclaw/skills"     // 用户级
    );

    /**
     * 是否启用文件监听（热更新）
     */
    private boolean watchEnabled = true;

    /**
     * 是否启用自动选择（索引注入 prompt）
     */
    private boolean autoSelectEnabled = true;

    /**
     * 索引 token 预算（超过则截断）
     */
    private int indexTokenBudget = 2000;
}
```

**Step 2: SkillRegistry**

```java
package com.jaguarliu.ai.skills;

import com.jaguarliu.ai.skills.gating.GatingResult;
import com.jaguarliu.ai.skills.gating.SkillGatingService;
import com.jaguarliu.ai.skills.model.*;
import com.jaguarliu.ai.skills.parser.SkillParseResult;
import com.jaguarliu.ai.skills.parser.SkillParser;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill 注册表
 *
 * 核心职责：
 * 1. Discovery：扫描文件系统，发现所有 skill
 * 2. Gating：检查可用性，标记 available/unavailable
 * 3. Indexing：构建 token-aware 的索引
 * 4. Loading：按需加载完整 skill 内容
 *
 * 设计原则：
 * - 文件系统即真相源
 * - Load-time gating
 * - Progressive disclosure
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillRegistry {

    private final SkillsProperties properties;
    private final SkillParser parser;
    private final SkillGatingService gatingService;

    /** 核心数据：name → SkillEntry */
    private final Map<String, SkillEntry> entries = new ConcurrentHashMap<>();

    /** 当前快照版本（用于会话一致性） */
    @Getter
    private volatile long snapshotVersion = 0;

    @PostConstruct
    public void init() {
        log.info("Initializing skill registry...");
        refresh();
    }

    /**
     * 刷新注册表（重新扫描文件系统）
     */
    public synchronized void refresh() {
        Map<String, SkillEntry> newEntries = new HashMap<>();
        Set<String> seenNames = new HashSet<>();

        List<String> paths = properties.getPaths();
        for (int priority = 0; priority < paths.size(); priority++) {
            Path skillsDir = resolvePath(paths.get(priority));
            if (Files.isDirectory(skillsDir)) {
                scanDirectory(skillsDir, priority, newEntries, seenNames);
            }
        }

        // 原子替换
        entries.clear();
        entries.putAll(newEntries);
        snapshotVersion++;

        // 统计信息
        long availableCount = entries.values().stream().filter(SkillEntry::isAvailable).count();
        log.info("Skill registry refreshed (v{}). Total: {}, Available: {}, Skills: {}",
                snapshotVersion, entries.size(), availableCount,
                entries.keySet());
    }

    /**
     * 获取所有可用的 skill（已通过 gating）
     */
    public List<SkillEntry> listAvailable() {
        return entries.values().stream()
                .filter(SkillEntry::isAvailable)
                .sorted(Comparator.comparingInt(e -> e.getMetadata().getPriority()))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有 skill（包括不可用的）
     */
    public List<SkillEntry> listAll() {
        return new ArrayList<>(entries.values());
    }

    /**
     * 按名称获取 skill entry
     */
    public Optional<SkillEntry> get(String name) {
        return Optional.ofNullable(entries.get(name));
    }

    /**
     * 检查 skill 是否存在且可用
     */
    public boolean isAvailable(String name) {
        SkillEntry entry = entries.get(name);
        return entry != null && entry.isAvailable();
    }

    /**
     * 加载 skill 完整内容（Progressive Disclosure 的"展开"阶段）
     * 每次从文件系统读取，确保最新
     */
    public Optional<LoadedSkill> load(String name) {
        SkillEntry entry = entries.get(name);
        if (entry == null) {
            log.warn("Skill not found: {}", name);
            return Optional.empty();
        }

        if (!entry.isAvailable()) {
            log.warn("Skill not available: {} (reason: {})", name, entry.getUnavailableReason());
            return Optional.empty();
        }

        // 从文件系统加载完整内容
        SkillMetadata metadata = entry.getMetadata();
        SkillParseResult result = parser.parse(metadata.getSourcePath(), metadata.getPriority());

        if (!result.isValid()) {
            log.error("Failed to load skill {}: {}", name, result.getErrorMessage());
            return Optional.empty();
        }

        return Optional.of(LoadedSkill.builder()
                .name(metadata.getName())
                .description(metadata.getDescription())
                .body(result.getBody())
                .allowedTools(metadata.getAllowedTools() != null ?
                        new HashSet<>(metadata.getAllowedTools()) : null)
                .confirmBefore(metadata.getConfirmBefore() != null ?
                        new HashSet<>(metadata.getConfirmBefore()) : null)
                .runtimeEnv(Map.of())  // 运行时 env 由调用方注入
                .build());
    }

    /**
     * 更新单个 skill（文件变化时调用）
     */
    public void updateSkill(Path skillFile) {
        // 确定优先级
        int priority = determinePriority(skillFile);

        SkillParseResult result = parser.parse(skillFile, priority);
        if (!result.isValid()) {
            log.warn("Invalid skill file {}: {}", skillFile, result.getErrorMessage());
            return;
        }

        SkillMetadata metadata = result.getMetadata();
        String name = metadata.getName();

        // 检查是否被高优先级覆盖
        SkillEntry existing = entries.get(name);
        if (existing != null && existing.getMetadata().getPriority() < priority) {
            log.debug("Ignoring lower priority skill: {} (existing priority: {})",
                    name, existing.getMetadata().getPriority());
            return;
        }

        // Gating 检查
        GatingResult gating = gatingService.check(metadata.getRequires());

        SkillEntry entry = SkillEntry.builder()
                .metadata(metadata)
                .available(gating.isAvailable())
                .unavailableReason(gating.getFormattedReason())
                .lastModified(result.getLastModified())
                .tokenCost(SkillEntry.calculateTokenCost(metadata))
                .build();

        entries.put(name, entry);
        snapshotVersion++;

        log.info("Updated skill: {} (available: {}, tokens: {})",
                name, gating.isAvailable(), entry.getTokenCost());
    }

    /**
     * 移除 skill
     */
    public void removeSkill(String name) {
        if (entries.remove(name) != null) {
            snapshotVersion++;
            log.info("Removed skill: {}", name);
        }
    }

    /**
     * 通过文件路径查找 skill 名称
     */
    public Optional<String> findNameByPath(Path skillFile) {
        return entries.values().stream()
                .filter(e -> e.getMetadata().getSourcePath().equals(skillFile))
                .map(e -> e.getMetadata().getName())
                .findFirst();
    }

    /**
     * 计算当前索引的总 token 成本
     */
    public int calculateTotalTokenCost() {
        return entries.values().stream()
                .filter(SkillEntry::isAvailable)
                .mapToInt(SkillEntry::getTokenCost)
                .sum();
    }

    // ============ Private Methods ============

    private void scanDirectory(Path skillsDir, int priority,
                               Map<String, SkillEntry> cache, Set<String> seenNames) {
        try (Stream<Path> dirs = Files.list(skillsDir)) {
            dirs.filter(Files::isDirectory).forEach(skillDir -> {
                Path skillFile = skillDir.resolve("SKILL.md");
                if (Files.exists(skillFile)) {
                    processSkillFile(skillFile, priority, cache, seenNames);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to scan directory: {}", skillsDir, e);
        }
    }

    private void processSkillFile(Path skillFile, int priority,
                                  Map<String, SkillEntry> cache, Set<String> seenNames) {
        SkillParseResult result = parser.parse(skillFile, priority);

        if (!result.isValid()) {
            log.warn("Invalid skill file {}: {}", skillFile, result.getErrorMessage());
            return;
        }

        SkillMetadata metadata = result.getMetadata();
        String name = metadata.getName();

        // 高优先级覆盖低优先级
        if (seenNames.contains(name)) {
            log.debug("Skipping lower priority skill: {} at {}", name, skillFile);
            return;
        }

        seenNames.add(name);

        // Gating 检查
        GatingResult gating = gatingService.check(metadata.getRequires());

        SkillEntry entry = SkillEntry.builder()
                .metadata(metadata)
                .available(gating.isAvailable())
                .unavailableReason(gating.getFormattedReason())
                .lastModified(result.getLastModified())
                .tokenCost(SkillEntry.calculateTokenCost(metadata))
                .build();

        cache.put(name, entry);

        if (gating.isAvailable()) {
            log.debug("Loaded skill: {} (tokens: {})", name, entry.getTokenCost());
        } else {
            log.debug("Loaded skill (unavailable): {} (reason: {})", name, gating.getFormattedReason());
        }
    }

    private int determinePriority(Path skillFile) {
        String absolutePath = skillFile.toAbsolutePath().toString();
        List<String> paths = properties.getPaths();

        for (int i = 0; i < paths.size(); i++) {
            Path configPath = resolvePath(paths.get(i)).toAbsolutePath();
            if (absolutePath.startsWith(configPath.toString())) {
                return i;
            }
        }
        return paths.size(); // 未知来源，最低优先级
    }

    private Path resolvePath(String pathConfig) {
        if (pathConfig.startsWith("~")) {
            String home = System.getProperty("user.home");
            return Paths.get(home + pathConfig.substring(1));
        }
        return Paths.get(pathConfig);
    }
}
```

**Step 3: 更新 application.yml**

```yaml
# Skills 配置
skills:
  paths:
    - .miniclaw/skills
    - ~/.miniclaw/skills
  watch-enabled: true
  auto-select-enabled: true
  index-token-budget: 2000
```

**Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/skills/SkillRegistry.java
git add src/main/java/com/jaguarliu/ai/skills/SkillsProperties.java
git add src/main/resources/application.yml
git commit -m "feat(skills): [P3-04] add skill registry with gating"
```

---

### Task P3-05: 文件监听器（热更新）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/skills/SkillFileWatcher.java`

**实现要点：**
- 使用 Java WatchService 监听文件变化
- 变更后更新 SkillRegistry
- 同一 session 内保持快照一致性（通过 snapshotVersion）

```java
package com.jaguarliu.ai.skills;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Skill 文件监听器
 *
 * 实时监听 skill 目录变化，触发 SkillRegistry 更新
 * 变更在下一个 turn 或新 session 生效（通过 snapshotVersion 控制）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillFileWatcher {

    private final SkillsProperties properties;
    private final SkillRegistry registry;

    private WatchService watchService;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<WatchKey, PathInfo> watchKeyMap = new HashMap<>();

    @PostConstruct
    public void start() {
        if (!properties.isWatchEnabled()) {
            log.info("Skill file watcher is disabled");
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "skill-watcher");
                t.setDaemon(true);
                return t;
            });

            List<String> paths = properties.getPaths();
            for (int priority = 0; priority < paths.size(); priority++) {
                registerDirectory(paths.get(priority), priority);
            }

            running.set(true);
            executor.submit(this::watchLoop);

            log.info("Skill file watcher started");
        } catch (IOException e) {
            log.error("Failed to start skill file watcher", e);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Error closing watch service", e);
            }
        }

        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("Skill file watcher stopped");
    }

    private void registerDirectory(String pathConfig, int priority) {
        Path skillsDir = resolvePath(pathConfig);
        if (!Files.isDirectory(skillsDir)) {
            log.debug("Skill directory does not exist: {}", skillsDir);
            return;
        }

        try {
            // 监听根目录（新增/删除 skill 文件夹）
            WatchKey key = skillsDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            watchKeyMap.put(key, new PathInfo(skillsDir, priority, true));
            log.info("Watching skill directory: {}", skillsDir);

            // 监听每个 skill 子目录（SKILL.md 变化）
            try (var stream = Files.list(skillsDir)) {
                stream.filter(Files::isDirectory).forEach(subDir -> {
                    try {
                        WatchKey subKey = subDir.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE);
                        watchKeyMap.put(subKey, new PathInfo(subDir, priority, false));
                    } catch (IOException e) {
                        log.warn("Failed to watch: {}", subDir, e);
                    }
                });
            }
        } catch (IOException e) {
            log.warn("Failed to register watch for: {}", skillsDir, e);
        }
    }

    private void watchLoop() {
        while (running.get()) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) continue;

                PathInfo info = watchKeyMap.get(key);
                if (info == null) {
                    key.reset();
                    continue;
                }

                // 防抖：收集短时间内的所有事件
                Thread.sleep(100);

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    Path changedPath = info.path.resolve(((WatchEvent<Path>) event).context());
                    handleChange(kind, changedPath, info);
                }

                key.reset();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (Exception e) {
                log.error("Error in watch loop", e);
            }
        }
    }

    private void handleChange(WatchEvent.Kind<?> kind, Path changedPath, PathInfo info) {
        String fileName = changedPath.getFileName().toString();

        // 1. 根目录下新建了文件夹（新 skill）
        if (info.isRoot && kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changedPath)) {
            log.info("New skill directory: {}", changedPath);

            // 注册新目录的监听
            try {
                WatchKey subKey = changedPath.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                watchKeyMap.put(subKey, new PathInfo(changedPath, info.priority, false));
            } catch (IOException e) {
                log.warn("Failed to watch new directory: {}", changedPath, e);
            }

            // 检查是否有 SKILL.md
            Path skillFile = changedPath.resolve("SKILL.md");
            if (Files.exists(skillFile)) {
                registry.updateSkill(skillFile);
            }
            return;
        }

        // 2. SKILL.md 文件变化
        if ("SKILL.md".equals(fileName)) {
            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                registry.findNameByPath(changedPath).ifPresent(registry::removeSkill);
            } else {
                registry.updateSkill(changedPath);
            }
            return;
        }

        // 3. skill 目录被删除
        if (info.isRoot && kind == StandardWatchEventKinds.ENTRY_DELETE) {
            Path skillFile = changedPath.resolve("SKILL.md");
            registry.findNameByPath(skillFile).ifPresent(registry::removeSkill);
        }
    }

    private Path resolvePath(String pathConfig) {
        if (pathConfig.startsWith("~")) {
            String home = System.getProperty("user.home");
            return Paths.get(home + pathConfig.substring(1));
        }
        return Paths.get(pathConfig);
    }

    private record PathInfo(Path path, int priority, boolean isRoot) {}
}
```

**Commit:**

```bash
git add src/main/java/com/jaguarliu/ai/skills/SkillFileWatcher.java
git commit -m "feat(skills): [P3-05] add file watcher for hot reload"
```

---

### Task P3-06: Skill 索引构建器

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/skills/SkillIndexBuilder.java`

**职责：**
- 构建 token-aware 的 XML 索引
- 支持 budget 控制（超出预算时截断）
- 生成紧凑的 system prompt 片段

```java
package com.jaguarliu.ai.skills;

import com.jaguarliu.ai.skills.model.SkillEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Skill 索引构建器
 *
 * 职责：
 * 1. 构建 token-aware 的 XML 索引
 * 2. 支持 budget 控制
 * 3. 生成 Progressive Disclosure 的"索引"阶段内容
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillIndexBuilder {

    private final SkillRegistry registry;
    private final SkillsProperties properties;

    // 基础开销（XML 结构、说明文字等）
    private static final int BASE_OVERHEAD_TOKENS = 150;

    /**
     * 构建 skill 索引（注入 system prompt）
     */
    public String buildIndex() {
        List<SkillEntry> available = registry.listAvailable();

        if (available.isEmpty()) {
            return "";
        }

        // 按优先级排序
        available = available.stream()
                .sorted(Comparator.comparingInt(e -> e.getMetadata().getPriority()))
                .collect(Collectors.toList());

        // 计算 budget
        int budget = properties.getIndexTokenBudget();
        int usedTokens = BASE_OVERHEAD_TOKENS;

        StringBuilder skillsXml = new StringBuilder();
        int includedCount = 0;

        for (SkillEntry entry : available) {
            int cost = entry.getTokenCost();
            if (usedTokens + cost > budget) {
                log.debug("Token budget exceeded. Included {} of {} skills.", includedCount, available.size());
                break;
            }

            usedTokens += cost;
            includedCount++;

            skillsXml.append(String.format(
                    "  <skill name=\"%s\">%s</skill>\n",
                    escapeXml(entry.getMetadata().getName()),
                    escapeXml(entry.getMetadata().getDescription())
            ));
        }

        if (includedCount == 0) {
            return "";
        }

        // 构建完整索引
        StringBuilder sb = new StringBuilder();
        sb.append("\n---\n\n");
        sb.append("## Available Skills\n\n");
        sb.append("The following skills are available. To use a skill:\n");
        sb.append("- Manual: User types `/skill-name arguments`\n");
        sb.append("- Auto: Respond with `[USE_SKILL:skill-name]` when appropriate\n\n");
        sb.append("<skills>\n");
        sb.append(skillsXml);
        sb.append("</skills>\n\n");
        sb.append("When you use `[USE_SKILL:xxx]`, the system will load the full skill instructions.\n");

        log.debug("Built skill index: {} skills, ~{} tokens", includedCount, usedTokens);

        return sb.toString();
    }

    /**
     * 计算索引的 token 成本
     */
    public int calculateIndexCost() {
        return BASE_OVERHEAD_TOKENS + registry.calculateTotalTokenCost();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
```

**Commit:**

```bash
git add src/main/java/com/jaguarliu/ai/skills/SkillIndexBuilder.java
git commit -m "feat(skills): [P3-06] add token-aware skill index builder"
```

---

### Task P3-07: Skill 选择器

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/skills/SkillSelector.java`
- Create: `src/main/java/com/jaguarliu/ai/skills/model/SkillSelection.java`

**职责：**
- 手动触发：解析 `/skill-name args`
- 自动选择：解析 LLM 的 `[USE_SKILL:xxx]`

```java
// SkillSelection.java
package com.jaguarliu.ai.skills.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class SkillSelection {
    private boolean selected;
    private String skillName;
    private String arguments;
    private LoadedSkill loadedSkill;
    private SelectionSource source;

    public enum SelectionSource {
        MANUAL,     // /skill-name args
        PROMPT,     // [USE_SKILL:xxx]
        NONE
    }

    public static SkillSelection none() {
        return SkillSelection.builder()
                .selected(false)
                .source(SelectionSource.NONE)
                .build();
    }

    public static SkillSelection pendingPromptSelection() {
        return SkillSelection.builder()
                .selected(false)
                .source(SelectionSource.PROMPT)
                .build();
    }
}
```

```java
// SkillSelector.java
package com.jaguarliu.ai.skills;

import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.model.SkillSelection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillSelector {

    private static final Pattern SLASH_COMMAND = Pattern.compile("^/(\\S+)(?:\\s+(.*))?$", Pattern.DOTALL);
    private static final Pattern USE_SKILL = Pattern.compile("\\[USE_SKILL:(\\S+?)\\]");

    private final SkillRegistry registry;
    private final SkillsProperties properties;

    /**
     * 根据用户输入选择 skill
     */
    public SkillSelection select(String userInput) {
        // 1. 手动触发
        SkillSelection manual = tryManualSelection(userInput);
        if (manual.isSelected()) {
            return manual;
        }

        // 2. 自动选择模式
        if (properties.isAutoSelectEnabled() && !registry.listAvailable().isEmpty()) {
            return SkillSelection.pendingPromptSelection();
        }

        return SkillSelection.none();
    }

    /**
     * 解析 /skill-name args 格式
     */
    public SkillSelection tryManualSelection(String userInput) {
        if (userInput == null || !userInput.startsWith("/")) {
            return SkillSelection.none();
        }

        Matcher matcher = SLASH_COMMAND.matcher(userInput.trim());
        if (!matcher.matches()) {
            return SkillSelection.none();
        }

        String skillName = matcher.group(1);
        String arguments = matcher.group(2);

        Optional<LoadedSkill> loaded = registry.load(skillName);
        if (loaded.isEmpty()) {
            log.warn("Skill not found or unavailable: {}", skillName);
            return SkillSelection.none();
        }

        log.info("Manual skill activation: {}", skillName);

        return SkillSelection.builder()
                .selected(true)
                .skillName(skillName)
                .arguments(arguments)
                .loadedSkill(loaded.get())
                .source(SkillSelection.SelectionSource.MANUAL)
                .build();
    }

    /**
     * 从 LLM 回复中解析 [USE_SKILL:xxx]
     */
    public SkillSelection parseFromLlmResponse(String llmResponse, String originalInput) {
        if (llmResponse == null) {
            return SkillSelection.none();
        }

        Matcher matcher = USE_SKILL.matcher(llmResponse);
        if (!matcher.find()) {
            return SkillSelection.none();
        }

        String skillName = matcher.group(1);
        Optional<LoadedSkill> loaded = registry.load(skillName);

        if (loaded.isEmpty()) {
            log.warn("LLM requested unavailable skill: {}", skillName);
            return SkillSelection.none();
        }

        log.info("LLM skill activation: {}", skillName);

        return SkillSelection.builder()
                .selected(true)
                .skillName(skillName)
                .arguments(originalInput)
                .loadedSkill(loaded.get())
                .source(SkillSelection.SelectionSource.PROMPT)
                .build();
    }
}
```

**Commit:**

```bash
git add src/main/java/com/jaguarliu/ai/skills/model/SkillSelection.java
git add src/main/java/com/jaguarliu/ai/skills/SkillSelector.java
git commit -m "feat(skills): [P3-07] add skill selector"
```

---

### Task P3-08: Skill 编译器

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/skills/SkillCompiler.java`

**职责：**
- 编译 skill 正文，替换 `$ARGUMENTS`
- 构建激活后的 system prompt

```java
package com.jaguarliu.ai.skills;

import com.jaguarliu.ai.skills.model.LoadedSkill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SkillCompiler {

    private static final String ARGUMENTS_PLACEHOLDER = "$ARGUMENTS";

    /**
     * 编译 skill 正文
     */
    public String compile(LoadedSkill skill, String arguments) {
        String body = skill.getBody();

        if (arguments != null && !arguments.isBlank()) {
            body = body.replace(ARGUMENTS_PLACEHOLDER, arguments.trim());
        } else {
            body = body.replace(ARGUMENTS_PLACEHOLDER, "(no arguments provided)");
        }

        return body;
    }

    /**
     * 构建激活 skill 后的 system prompt
     */
    public String buildSystemPrompt(String basePrompt, LoadedSkill skill, String arguments) {
        StringBuilder sb = new StringBuilder();

        if (basePrompt != null && !basePrompt.isBlank()) {
            sb.append(basePrompt).append("\n\n");
        }

        sb.append("---\n\n");
        sb.append("## Active Skill: ").append(skill.getName()).append("\n\n");
        sb.append(compile(skill, arguments));

        if (skill.getAllowedTools() != null && !skill.getAllowedTools().isEmpty()) {
            sb.append("\n\n---\n");
            sb.append("**Tool Restriction**: Only use: ");
            sb.append(String.join(", ", skill.getAllowedTools()));
        }

        return sb.toString();
    }
}
```

**Commit:**

```bash
git add src/main/java/com/jaguarliu/ai/skills/SkillCompiler.java
git commit -m "feat(skills): [P3-08] add skill compiler"
```

---

### Task P3-09: 集成 ContextBuilder

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/ContextBuilder.java`

（集成 SkillIndexBuilder、SkillSelector、SkillCompiler）

**Commit:**

```bash
git add src/main/java/com/jaguarliu/ai/runtime/ContextBuilder.java
git commit -m "feat(skills): [P3-09] integrate skills into ContextBuilder"
```

---

### Task P3-10: 集成 ToolDispatcher（权限控制）

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/tools/ToolDispatcher.java`

添加 `isToolAllowed()` 和 `requiresHitl()` 方法支持 skill 配置覆盖。

**Commit:**

```bash
git add src/main/java/com/jaguarliu/ai/tools/ToolDispatcher.java
git commit -m "feat(skills): [P3-10] add skill permission checks"
```

---

### Task P3-11: 示例 Skills

**Files:**
- Create: `.miniclaw/skills/code-review/SKILL.md`
- Create: `.miniclaw/skills/git-commit/SKILL.md`
- Create: `.miniclaw/skills/explain-code/SKILL.md`

**Commit:**

```bash
git add .miniclaw/skills/
git commit -m "feat(skills): [P3-11] add example skills"
```

---

## 验收标准

### 核心功能

1. **Discovery + Gating**
   - 启动日志：`Skill registry refreshed (v1). Total: 3, Available: 2`
   - 缺少 env/bins 的 skill 被标记为 unavailable

2. **Progressive Disclosure**
   - 未激活时：system prompt 只包含紧凑 XML 索引
   - 激活后：加载完整 skill 正文

3. **手动触发**
   - `/code-review src/main/java/...` 激活 skill
   - `allowed-tools` 限制工具调用

4. **自动选择**
   - 用户说"帮我审查代码"
   - LLM 回复 `[USE_SKILL:code-review]`
   - 系统加载 skill 并继续执行

5. **热更新**
   - 修改 SKILL.md → 日志显示 "Updated skill: xxx"
   - 删除 skill 目录 → 日志显示 "Removed skill: xxx"
   - 变更在下一个 turn 生效

6. **Token 成本控制**
   - 索引不超过 `index-token-budget`
   - 日志显示：`Built skill index: 3 skills, ~450 tokens`

---

## 设计对比

| OpenClaw 特性 | MiniClaw 实现 |
|--------------|---------------|
| Discovery (多位置扫描) | ✅ 项目级 > 用户级 > 内置 |
| Gating (env/bins/config/os) | ✅ SkillGatingService |
| Progressive Disclosure | ✅ 索引 → 激活 → 展开 |
| Token 成本控制 | ✅ budget + 截断 |
| 热更新 (WatchService) | ✅ SkillFileWatcher |
| 运行时 env 注入 | ✅ LoadedSkill.runtimeEnv |
| Slash command | ✅ /skill-name args |
| 数据库存储 | ❌ 不需要（文件系统即真相源）|
| 向量检索 | ❌ 不需要（提示词驱动）|
