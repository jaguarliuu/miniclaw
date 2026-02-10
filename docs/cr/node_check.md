# Node Check - 代码审查报告

## 1. 总览结论

你当前实现已经具备一个可用的"节点资产管理 + 凭据加密存储 + 远程执行"闭环，但存在几类高风险问题：

- **执行超时不可靠** - 命令卡住会无限占线程
- **输出读取可能 OOM** - 大输出在 Connector 内存累积
- **安全策略未真正生效** - strict/standard/relaxed 只是字段
- **日志存在潜在泄密** - 异常 message/命令可能打到日志
- **并发/一致性细节不足** - alias 并发注册竞态、重复 connector 覆盖
- **扩展准备不足** - K8s/DB 类型的凭据类型、连接方式、schema 还没抽象好

---

## 2. 缺陷报告（按严重级别）

### P0 - 高危必须修（安全/稳定性）

#### P0-1：SSH 命令执行"硬超时"缺失

**现象**

`SshConnector.execute()` 只对 connect 阶段设置 timeout，读取输出用 `while(true)` 循环，没有总耗时截止。

**风险**

遇到 `tail -f`、网络阻塞、命令挂死会无限占用线程，导致网关/服务被拖死。

**建议修复**

- 增加统一的 deadline/硬超时：超过执行超时立即中断 channel/session 并返回可解释的超时结果
- 将 `sshTimeoutSeconds` 与 `execTimeoutSeconds` 明确区分并贯穿调用

---

#### P0-2：输出读取在 Connector 内存无限累积，存在 OOM 风险

**现象**

Connector 中把 stdout/stderr 全部读入 `ByteArrayOutputStream`，Service 才截断。

**风险**

大输出会在 Connector 内存暴涨，导致 OOM、Full GC、服务抖动；400+ 节点巡检时放大风险。

**建议修复**

- 在 Connector 内部实现输出上限（max bytes/chars），达到上限停止读取并标记 `truncated`
- 结构化返回 `truncated`/`originalLength`，避免仅靠 substring

---

#### P0-3：安全策略字段未落地，等于"远程任意命令执行"

**现象**

Node 有 `safetyPolicy`，Service 只返回/保存，不检查命令。

**风险**

只要能调用 `executeCommand`，就具备全网 RCE 能力；误操作或 prompt 注入后果极大。

**建议修复**

- 增加 `SafetyPolicyGuard`：至少实现 strict allowlist / standard denylist / relaxed minimal deny
- 将策略检查放在 Service（靠近业务入口），而不是 Connector
- 对 K8s/DB 也要有策略：SQL 白名单/禁止 DDL、kubectl 允许的 verb/resource 白名单等

---

#### P0-4：日志潜在泄密（命令、异常 message）

**现象**

- `NodeService` 记录 `Executing command... command`
- SSH connector / handler 记录 `e.getMessage()`

**风险**

命令中可能包含 token、密钥、内网地址、curl header；异常 message 可能包含 host、用户名、路径信息。

**建议修复**

- 默认日志不打印 command，只记录摘要（长度/哈希/类型）
- 失败日志只打印异常类型；堆栈放 debug
- RPC 返回 message 同样要保守，避免把底层异常细节透出

---

#### P0-5：SSH 默认 host=localhost / username=root 存在误操作风险

**现象**

`SshConnector.createSession` 对 host/username 做默认值。

**风险**

节点配置缺字段时会在网关本机 root/localhost 执行命令，属于高危误执行。

**建议修复**

- Service 注册/更新时按 connector 类型强校验必填字段（ssh 必须 host/port/username）
- Connector 内不做危险默认值，宁可失败

---

### P1 - 重要建议修（正确性/一致性/可维护性）

#### P1-1：alias 唯一校验存在并发竞态

**现象**

`existsByAlias` + `save` 不是原子操作。

**风险**

并发注册同 alias 时一个会 DB 异常，错误体验不一致；对多租户扩展更麻烦。

**建议修复**

- DB 层 unique index 作为唯一真相；Service 捕获并转换成明确的冲突错误码
- 需要决定 alias 是否大小写敏感：建议统一小写并建立 `lower(alias)` 唯一索引（PG）

---

#### P1-2：ConnectorFactory 注册可能静默覆盖同 type

**现象**

`registry.put(type, connector)`

**风险**

两个 Bean type 相同会覆盖，行为不确定。

**建议修复**

- 使用 `putIfAbsent`，若重复 type 直接启动失败

---

#### P1-3：tags 设计过于松散，过滤逻辑可能误判

**现象**

tags 是 TEXT，filter 用 contains（之前 service 逻辑）。

**风险**

误命中（db vs db2），影响筛选与巡检分组。

**建议修复**

- **轻量方案**：规范 tags 存储格式（逗号分隔、trim、lower），过滤时 split 精确匹配
- **长期方案**：tags 做关联表或数组类型（PG array + GIN）以支持高效查询

---

#### P1-4：Connector 接口对未来扩展不够"可配置"

**现象**

execute 只传 `timeoutSeconds`，无法传 maxOutput、工作目录、环境变量、审计上下文等。

**风险**

后面接入 K8s/DB 时会不断扩参数，导致签名膨胀。

**建议修复**

- 将执行参数封装为 `ExecOptions`（timeout、maxOutput、context、dryRun、labels 等）
- 返回结构化 `ExecResult`（stdout/stderr/exitCode/truncated/timedOut）

---

#### P1-5：CredentialCipher 初始化与失败行为

**现象**

未配置 key 时 init 只 warn，但 encrypt/decrypt 才抛异常。

**风险**

服务可启动但功能不可用，线上容易踩坑；warn 可能刷屏。

**建议修复**

- 明确策略：要么启动即失败（更安全），要么 health check 标红并在 UI 提示
- 增加 key 轮换预案（见扩展部分）

---

### P2 - 体验/扩展性建议（可排后面）

#### P2-1：RPC payload 解析用 Map 强转，不够稳

**风险**

字段类型变化、前端传字符串/数字，会出现 ClassCast 或空指针。

**建议**

定义 DTO，ObjectMapper 直接映射，统一参数校验与错误码。

---

#### P2-2：NodeEntity 字段类型与审计

**风险**

后续多租户/多环境需要更多字段：tenant、env、region、owner、fingerprint 等。

**建议**

提前预留字段或扩展表（NodeMeta / NodeSecretRef）。

---

## 3. 修复建议与实施顺序（建议排期）

### 第一阶段（立刻提升稳定性与安全）

1. **SSH execute**：硬超时 + 输出上限（避免卡死与 OOM）
2. **日志脱敏**：不打 command / 不打异常 message
3. **SafetyPolicyGuard 落地**：strict/standard/relaxed 真正生效
4. **Service 校验**：ssh 必填 host/port/username；不允许 localhost/root 默认
5. **Factory 防重复 type 覆盖**
6. **DB unique 冲突捕获**并返回一致错误码（RPC CONFLICT）

### 第二阶段（为 400+ 节点巡检做准备）

1. **ExecResult 结构化返回**（truncated、exitCode、timedOut）
2. **增加"巡检聚合层"**：并发限制（如 20 并发）、失败重试策略、结果摘要存储
3. **listForLlm 输出最小化**：默认不下发 host（可 masked），只下发 alias/type/tags/policy/lastTest

### 第三阶段（可扩展架构：K8s + DB 多驱动）

1. **统一 Connector 能力模型**（见下一节）
2. **增加凭据模型与 schema**（避免把所有凭据当成一个字符串）
3. **增加策略插件**：K8sPolicy、SqlPolicy、SshPolicy
4. **增加审计与权限模型**（谁能执行什么、在哪些 tag/env 上执行）

---

## 4. 扩展设计建议（K8s + MySQL/PGSQL/高斯/达梦/Oracle）

你后面要接入的这些类型，最大挑战不是"多写几个 Connector"，而是**凭据类型、执行模型、策略模型、结果模型的统一抽象**。建议提前按下面方式铺好地基。

### 4.1 Connector 类型分类与"能力模型"

建议把 Connector 分成两大类：

#### A) Command 执行类（SSH、K8s exec）

- **输入**：命令/脚本/容器选择等
- **输出**：stdout/stderr/exitCode
- **风险**：命令注入、破坏性操作、超时、输出爆炸

#### B) Query 执行类（数据库）

- **输入**：SQL、参数、数据库名/schema
- **输出**：结果集（需要分页/行数限制/字段脱敏）
- **风险**：DDL/DML 破坏、慢查询拖垮、数据泄露、返回过大

因此建议 Connector 接口不要只叫 `execute(command)`，而是抽象为：

```java
executeAction(Action, ExecOptions)
```

Action 子类型可以是 `ShellCommandAction` / `K8sAction` / `SqlAction`，或者至少在 `ExecOptions` 中标明 `actionType`。

这样你以后扩一个新 DB，不需要改 Service/网关的整体逻辑，只加 Action+Connector 实现。

---

### 4.2 凭据模型（强烈建议不要继续用"一个字符串"吃所有类型）

现在 credential 是一个明文字符串（加密存库），对于 ssh password/ssh key 还行，但对 K8s 和数据库不够：

- **K8s**：可能是 kubeconfig（多行 YAML）、token、client cert/key、context/namespace
- **DB**：可能是 user/pass、JDBC URL、SSL 证书、SID/serviceName、schema、读写角色等

建议引入概念：**Credential Payload 结构化**（即使最终仍以 JSON 字符串存储，也要有 schema）：

- `credentialType`：ssh_password / ssh_key / kubeconfig / db_password / db_wallet / …
- `credentialData`：JSON（加密后存）
- `credentialVersion`：用于轮换

并且 `NodeEntity` 最好把 `authType` 扩展为更明确的 enum（或字符串白名单），并按 `connectorType` 约束。

---

### 4.3 K8s Connector 的执行模型建议

K8s 常见动作：

- kubectl get/describe/logs
- exec into pod
- port-forward
- apply/delete（高危）

建议：

- **strict**：只允许 read-only（get/describe/logs）
- **standard**：允许 exec/logs，但禁止 apply/delete/scale/rollout restart
- **relaxed**：开放更多，但仍禁止明显高危（delete ns / delete pv）

另外，K8s 的输出非常大（pods 列表、logs），必须做分页/行数限制，并支持"只返回摘要 + 可继续拉取"。

---

### 4.4 数据库 Connector 的统一策略（MySQL/PG/高斯/达梦/Oracle）

不同 DB 的差异主要在：连接参数、驱动、方言；但你在平台层要统一 4 件事：

1. **连接配置标准化**：host/port/dbName/schema/user/pass/ssl/charset
2. **SQL 策略**：默认禁止 DDL/DML（只允许 SELECT），或者按 policy 控制
3. **结果限制**：默认 limit 行数、默认超时、字段脱敏（如手机号/邮箱）
4. **执行审计**：记录谁在何时对哪个节点执行了什么（至少记录 hash，不记录明文）

建议对 DB Connector 的默认行为：

- 默认 `readOnly=true`（连接层 setReadOnly + 事务只读）
- SQL parser 或简单正则：拦截 insert/update/delete/drop/alter/create/grant/revoke 等关键字
- 自动加 LIMIT（不同 DB 方言要处理：Oracle 用 `fetch first n rows only` 或 `rownum`）
- 结果集最多 N 行、最多 M 列、单元格最大长度限制

---

### 4.5 多租户/权限体系（提前预留）

你一旦接入数据库，权限就变高敏感了。建议提前考虑：

- Node 是否属于某个 tenant / project / environment
- RPC connectionId 是否映射到用户身份
- 不同用户对不同 tag/env 的执行权限（RBAC）

即使第一版不做 RBAC，也建议：

- Node 表预留 `owner`/`tenant`/`env`/`region` 字段（或 NodeMeta 表）
- 执行接口预留审计字段（`requesterId`、`traceId`）

---

### 4.6 密钥轮换与安全运营

`CredentialCipher` 用固定 key（hex 64）没问题，但长期一定要轮换：

- `keyId` + `activeKey` + `oldKey(s)`
- 解密时按 `keyId` 选择 key；加密用 `activeKey`
- 支持后台 re-encrypt（批量重加密）

第一阶段可以不做，但 schema 最好预留 `credentialKeyId`。

---

## 总结

本文档识别了当前实现中的关键问题，并按优先级（P0/P1/P2）给出了修复建议。建议按三个阶段逐步实施：

1. **第一阶段**：修复高危安全和稳定性问题
2. **第二阶段**：为大规模节点巡检做准备
3. **第三阶段**：建立可扩展架构以支持 K8s 和多种数据库

通过这些改进，系统将具备更好的安全性、稳定性和可扩展性。
