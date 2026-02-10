# MiniClaw 桌面应用安装指南

## 系统要求

- **Windows**: Windows 10 或更高版本
- **macOS**: macOS 10.13 (High Sierra) 或更高版本
- **内存**: 至少 4GB RAM
- **磁盘空间**: 至少 500MB 可用空间

## 安装步骤

### Windows

1. 下载 `MiniClaw-Setup.exe`
2. 双击运行安装程序
3. 按照安装向导完成安装
4. 安装完成后，从开始菜单或桌面快捷方式启动 MiniClaw

### macOS

1. 下载 `MiniClaw.dmg`
2. 双击打开 DMG 文件
3. 将 MiniClaw 图标拖动到 Applications 文件夹
4. 从 Applications 文件夹启动 MiniClaw
5. 首次运行时，如果系统提示"无法验证开发者"，请前往 **系统偏好设置 > 安全性与隐私**，点击"仍要打开"

## 首次启动

MiniClaw 首次启动时会自动完成以下设置：

1. ✅ **自动生成加密密钥**：无需手动配置环境变量
2. ✅ **创建应用数据目录**：
   - Windows: `C:\Users\<用户名>\AppData\Roaming\MiniClaw`
   - macOS: `~/Library/Application Support/MiniClaw`
3. ✅ **初始化数据库**：自动创建 SQLite 数据库
4. ✅ **创建工作空间**：准备好 workspace 目录供 Agent 使用

启动过程大约需要 10-30 秒，请耐心等待。

## 配置 LLM

首次启动后，您需要配置 LLM（大语言模型）服务：

1. 应用会自动打开设置页面
2. 填写以下信息：
   - **LLM Endpoint**: 例如 `https://api.deepseek.com` 或 `http://localhost:11434` (Ollama)
   - **API Key**: 您的 API 密钥
   - **Model**: 模型名称，例如 `deepseek-chat` 或 `llama2`
3. 点击"Test Connection"验证连接
4. 点击"Save"保存配置

### 支持的 LLM 服务

- **DeepSeek**: `https://api.deepseek.com`
- **通义千问**: `https://dashscope.aliyuncs.com/compatible-mode`
- **GLM (智谱)**: `https://open.bigmodel.cn/api/paas/v4`
- **Ollama (本地)**: `http://localhost:11434`
- **OpenAI 兼容接口**: 任何兼容 OpenAI API 的服务

## 常见问题

### 应用启动失败

如果应用启动失败，您会看到一个错误对话框，显示具体的错误信息。常见问题包括：

#### 1. 端口被占用

**错误消息**: "Port Already in Use"

**解决方案**:
- 关闭占用 18080 端口的其他应用
- 或者修改配置文件更改端口（高级用户）

#### 2. Java 运行时错误

**错误消息**: "Backend Failed to Start"

**解决方案**:
- 点击"View Logs"按钮查看详细日志
- 确保您的系统满足最低要求
- 尝试重新安装应用

#### 3. 后端崩溃

**错误消息**: "Backend Crashed"

**解决方案**:
- 点击"View Logs"按钮查看崩溃日志
- 检查是否有足够的可用内存
- 提交 issue 到 GitHub，附上日志信息

### 查看日志

如果遇到问题，可以查看应用日志：

1. **方法 1**: 在错误对话框中点击"View Logs"按钮
2. **方法 2**: 手动打开日志目录：
   - Windows: `C:\Users\<用户名>\AppData\Roaming\MiniClaw`
   - macOS: `~/Library/Application Support/MiniClaw`

日志文件位置：
- 应用配置: `config.json`
- 数据库: `miniclaw.db`
- 工作空间: `workspace/`

### 重置应用

如果需要完全重置应用到初始状态：

1. 关闭 MiniClaw
2. 删除应用数据目录：
   - Windows: `C:\Users\<用户名>\AppData\Roaming\MiniClaw`
   - macOS: `~/Library/Application Support/MiniClaw`
3. 重新启动 MiniClaw

**注意**: 这将删除所有数据，包括会话历史、配置等。

## 卸载

### Windows

1. 前往"控制面板 > 程序 > 程序和功能"
2. 找到"MiniClaw"
3. 点击"卸载"
4. 按照向导完成卸载
5. (可选) 手动删除应用数据目录 `C:\Users\<用户名>\AppData\Roaming\MiniClaw`

### macOS

1. 打开 Finder
2. 前往"Applications"文件夹
3. 将 MiniClaw 拖动到废纸篓
4. (可选) 手动删除应用数据目录 `~/Library/Application Support/MiniClaw`

## 更新

MiniClaw 支持自动更新：

1. 应用启动后会自动检查更新
2. 如果有新版本，会提示您下载
3. 下载完成后，您可以选择立即安装或稍后安装
4. 更新会在应用关闭时自动安装

您也可以手动检查更新：
- 前往菜单栏 **帮助 > 检查更新**

## 数据安全

### 加密密钥

MiniClaw 使用 AES-256-GCM 加密算法保护敏感数据（如 SSH 密码、密钥等）。加密密钥在首次启动时自动生成，存储在：

- Windows: `C:\Users\<用户名>\AppData\Roaming\MiniClaw\config.json`
- macOS: `~/Library/Application Support/MiniClaw/config.json`

**重要提示**:
- 请勿随意删除或修改 `config.json` 中的 `encryptionKey` 字段
- 如果密钥丢失，所有加密的凭据将无法恢复
- 建议定期备份 `config.json` 文件

### 数据备份

建议定期备份应用数据目录，包括：
- `config.json` - 配置和加密密钥
- `miniclaw.db` - 数据库（会话、节点、渠道等）
- `workspace/` - 工作空间文件

## 性能优化

### 内存使用

MiniClaw 的内存使用取决于：
- LLM 模型的复杂度
- 会话历史的长度
- 工作空间文件的大小

如果遇到内存不足：
1. 清理旧的会话历史
2. 删除不需要的工作空间文件
3. 使用更轻量的 LLM 模型

### 磁盘空间

定期清理以节省磁盘空间：
- 删除旧的 Memory 文件（`workspace/memory/`）
- 清理上传的临时文件（`workspace/uploads/`）
- 删除不需要的会话历史

## 网络要求

MiniClaw 需要以下网络访问：
- **LLM API**: 访问您配置的 LLM 服务端点
- **自动更新**: 连接 GitHub 检查和下载更新（可选）
- **Web Search**: 如果使用 Web Search 工具，需要访问搜索引擎 API

如果在企业防火墙后，请确保允许以上网络访问。

## 隐私声明

MiniClaw 是完全本地化的应用：
- ✅ 所有数据存储在本地
- ✅ 不会上传任何数据到我们的服务器
- ✅ 只与您配置的 LLM 服务通信
- ✅ 自动更新通过 GitHub Releases 进行

## 技术支持

如果遇到问题：

1. **查看文档**: 阅读完整的文档 [README.md](../README.md)
2. **搜索 Issues**: 在 GitHub 上搜索类似问题
3. **提交 Issue**: 在 GitHub 创建新 issue，附上：
   - 错误消息截图
   - 日志文件（从应用数据目录复制）
   - 系统信息（操作系统版本、内存等）
4. **社区讨论**: 加入我们的社区讨论

## 致谢

感谢使用 MiniClaw！我们会持续改进产品，欢迎您的反馈和建议。

---

**版本**: 1.0.0
**最后更新**: 2024-02-10
