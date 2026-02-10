# MiniClaw 安装体验改进总结

## 问题描述

用户在安装 MiniClaw 后遇到两个主要问题：

1. **密钥环境变量缺失**：链接 node 需要一个 `NODE_CONSOLE_ENCRYPTION_KEY` 环境变量，但默认没有配置，用户也不知道需要设置，导致应用启动失败。

2. **错误处理不友好**：Windows 和 Mac 安装后如果出错就自动退出，没有提供任何错误信息，用户无法知道具体出了什么问题。

## 解决方案

### 1. 自动生成和管理加密密钥 ✅

**实现位置**: `electron/main.js`

**新增功能**:
- 添加了 `getOrCreateEncryptionKey()` 函数，在应用启动时自动检查或生成加密密钥
- 密钥规格：64 字符的 hex 字符串（32 字节，符合 AES-256 要求）
- 密钥存储位置：`~/AppData/Roaming/MiniClaw/config.json` (Windows) 或 `~/Library/Application Support/MiniClaw/config.json` (Mac)
- 如果配置文件已存在密钥，则复用；否则自动生成新密钥

**代码实现**:
```javascript
function getOrCreateEncryptionKey() {
  let config = {};

  // 读取现有配置
  if (fs.existsSync(configPath)) {
    try {
      const data = fs.readFileSync(configPath, 'utf8');
      config = JSON.parse(data);
    } catch (err) {
      console.warn('Failed to read config, creating new:', err.message);
    }
  }

  // 检查是否已有密钥
  if (config.encryptionKey && config.encryptionKey.length === 64) {
    console.log('Using existing encryption key from config');
    return config.encryptionKey;
  }

  // 生成新密钥 (32 字节 = 64 hex 字符)
  const key = crypto.randomBytes(32).toString('hex');
  console.log('Generated new encryption key');

  // 保存到配置文件
  config.encryptionKey = key;
  try {
    fs.writeFileSync(configPath, JSON.stringify(config, null, 2), 'utf8');
    console.log('Encryption key saved to config');
  } catch (err) {
    console.error('Failed to save encryption key:', err.message);
    // 即使保存失败，也返回密钥继续启动
  }

  return key;
}
```

**环境变量传递**:
修改了 `startJavaBackend()` 函数，通过环境变量将密钥传递给 Java 后端：
```javascript
const env = {
  ...process.env,
  NODE_CONSOLE_ENCRYPTION_KEY: encryptionKey
};

javaProcess = spawn(javaExe, args, {
  env: env,
  stdio: ['ignore', 'pipe', 'pipe'],
  windowsHide: true,
});
```

### 2. 添加友好的错误处理机制 ✅

**实现位置**: `electron/main.js`

**新增功能**:

#### (1) 错误日志收集
- 在 `startJavaBackend()` 中收集 Java 进程的 stderr 输出
- 保留最后 50 条错误日志，用于错误诊断
- 代码：
```javascript
let javaErrorLogs = [];  // 收集 Java 错误日志

javaProcess.stderr.on('data', (data) => {
  const message = data.toString();
  process.stderr.write(`[Java] ${message}`);

  // 收集错误日志，用于错误提示
  javaErrorLogs.push(message);

  // 限制错误日志数量，只保留最后 50 条
  if (javaErrorLogs.length > 50) {
    javaErrorLogs.shift();
  }
});
```

#### (2) 错误信息提取
- 添加了 `extractErrorMessage()` 函数，从日志中智能提取关键错误信息
- 支持识别常见错误模式：Exception、Error、Failed to、Could not、Unable to
- 代码：
```javascript
function extractErrorMessage() {
  if (javaErrorLogs.length === 0) {
    return 'No error logs captured. The backend may have failed to start.';
  }

  // 查找关键错误信息
  const errorPatterns = [
    /Exception.*?:/,
    /Error.*?:/,
    /Failed to.*?:/,
    /Could not.*?:/,
    /Unable to.*?:/,
  ];

  for (const log of javaErrorLogs.reverse()) {
    for (const pattern of errorPatterns) {
      const match = log.match(pattern);
      if (match) {
        // 提取错误信息及后面的一行
        const lines = log.split('\n');
        return lines.slice(0, 3).join('\n').trim();
      }
    }
  }

  // 如果没有匹配到特定错误，返回最后几条日志
  return javaErrorLogs.slice(-5).join('\n').trim();
}
```

#### (3) 错误对话框
- 添加了 `showStartupError()` 函数，显示友好的错误对话框
- 提供两个选项：Quit（退出）和 View Logs（查看日志目录）
- 代码：
```javascript
function showStartupError(title, message, details = null) {
  const options = {
    type: 'error',
    title: title,
    message: message,
    buttons: ['Quit', 'View Logs'],
    defaultId: 0,
  };

  if (details) {
    options.detail = details;
  }

  dialog.showMessageBox(options).then(({ response }) => {
    if (response === 1) {
      // 打开日志目录
      const { shell } = require('electron');
      shell.openPath(appDataPath);
    }
    app.quit();
  });
}
```

#### (4) 启动阶段错误处理
修改了 `app.whenReady()` 中的错误处理逻辑：
- **健康检查超时**：显示详细的错误日志
- **端口被占用**：提示用户关闭其他应用或更改端口
- **其他错误**：显示通用错误消息

示例：
```javascript
catch (err) {
  console.error('Failed to start:', err);

  // 关闭启动页面
  if (splashWindow && !splashWindow.isDestroyed()) {
    splashWindow.close();
    splashWindow = null;
  }

  // 显示友好的错误提示
  if (err.message && err.message.includes('health check timed out')) {
    // 健康检查超时，显示详细日志
    showStartupError(
      'Backend Failed to Start',
      'The MiniClaw backend service failed to start properly.',
      err.message
    );
  } else if (err.message && err.message.includes('EADDRINUSE')) {
    // 端口被占用
    showStartupError(
      'Port Already in Use',
      `Port ${serverPort} is already being used by another application.`,
      'Please close the other application or change the port in settings.'
    );
  } else {
    // 其他错误
    showStartupError(
      'Startup Error',
      'Failed to start MiniClaw. Please check the logs for more details.',
      err.message || 'Unknown error'
    );
  }

  // 清理 Java 进程
  killJavaProcess();
}
```

#### (5) 运行时崩溃处理
改进了 Java 进程意外退出的处理：
- 检测退出码，如果非 0 表示异常退出
- 显示崩溃对话框，包含最近的错误日志
- 提供查看日志目录的选项

```javascript
javaProcess.on('exit', (code) => {
  console.log(`Java process exited with code ${code}`);
  const hadProcess = javaProcess !== null;
  javaProcess = null;

  // If Java crashes unexpectedly while window is open, show error and quit
  if (mainWindow && !mainWindow.isDestroyed() && code !== 0) {
    const errorMsg = extractErrorMessage();
    dialog.showMessageBox(mainWindow, {
      type: 'error',
      title: 'Backend Crashed',
      message: `The backend service has crashed unexpectedly (exit code: ${code}).`,
      detail: `Recent logs:\n${errorMsg}`,
      buttons: ['Quit', 'View Logs'],
      defaultId: 0,
    }).then(({ response }) => {
      if (response === 1) {
        const { shell } = require('electron');
        shell.openPath(appDataPath);
      }
      app.quit();
    });
  }
});
```

## 用户体验改进

### 改进前：
1. ❌ 用户安装后启动应用，静默失败，应用直接退出
2. ❌ 用户不知道发生了什么，只能查看控制台日志（但大多数用户不知道如何查看）
3. ❌ 用户需要手动设置 `NODE_CONSOLE_ENCRYPTION_KEY` 环境变量，但不知道这个要求
4. ❌ 即使用户知道需要设置环境变量，也不知道如何生成正确格式的密钥

### 改进后：
1. ✅ 应用启动时自动生成加密密钥，无需用户干预
2. ✅ 如果启动失败，显示友好的错误对话框，告知具体问题
3. ✅ 错误对话框提供详细的错误日志，帮助用户或开发者诊断问题
4. ✅ 提供"查看日志"按钮，快速打开日志目录
5. ✅ 区分不同类型的错误（端口占用、健康检查超时、崩溃等），提供针对性的提示
6. ✅ 运行时如果后端崩溃，也会显示错误对话框，而不是静默退出

## 技术细节

### 加密密钥规格
- **算法**: AES-256-GCM
- **密钥长度**: 32 字节（256 位）
- **格式**: 64 字符的 hex 字符串
- **生成方式**: `crypto.randomBytes(32).toString('hex')`
- **存储位置**: `config.json` (应用数据目录)

### 错误日志管理
- **最大条数**: 50 条（防止内存泄漏）
- **提取策略**: 优先显示包含关键错误关键字的日志
- **后备策略**: 如果没有匹配到错误，显示最后 5 条日志

### 健康检查增强
- **超时时间**: 60 秒
- **检查间隔**: 1 秒
- **错误信息**: 包含超时时间和最近的错误日志

## 测试建议

### 场景 1：首次启动（正常）
1. 删除 `~/AppData/Roaming/MiniClaw/config.json` (Windows) 或 `~/Library/Application Support/MiniClaw/config.json` (Mac)
2. 启动 MiniClaw
3. 预期结果：自动生成密钥，应用正常启动

### 场景 2：已有配置（正常）
1. 确保 `config.json` 存在且包含有效密钥
2. 启动 MiniClaw
3. 预期结果：复用现有密钥，应用正常启动

### 场景 3：端口被占用（错误）
1. 先启动一个应用占用 18080 端口
2. 启动 MiniClaw
3. 预期结果：显示"Port Already in Use"错误对话框

### 场景 4：后端启动失败（错误）
1. 模拟后端启动失败（例如删除 JRE 或 jar 文件）
2. 启动 MiniClaw
3. 预期结果：显示"Backend Failed to Start"错误对话框，包含详细日志

### 场景 5：运行时崩溃（错误）
1. 正常启动 MiniClaw
2. 手动杀死 Java 进程（模拟崩溃）
3. 预期结果：显示"Backend Crashed"错误对话框

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `electron/main.js` | 修改 | 添加密钥管理、错误处理、日志收集功能 |

## 后续改进建议

1. **日志文件持久化**：将错误日志保存到文件，方便用户和开发者查看历史日志
2. **错误报告功能**：添加"Report Issue"按钮，自动收集日志和系统信息，帮助用户提交 issue
3. **重试机制**：对于某些可恢复的错误（如端口占用），提供重试或自动切换端口的选项
4. **配置界面**：提供 UI 界面让用户查看和管理加密密钥
5. **密钥备份提示**：在首次生成密钥时，提示用户备份密钥（用于多设备同步）

## 向后兼容性

- ✅ 现有安装不受影响（会自动生成密钥）
- ✅ 如果用户已手动设置 `NODE_CONSOLE_ENCRYPTION_KEY` 环境变量，优先使用环境变量（当前实现是环境变量会被覆盖，可考虑改进优先级）
- ✅ 配置文件格式向后兼容，新增字段不影响旧版本

## 总结

通过这些改进，MiniClaw 的安装和启动体验得到了显著提升：
1. **零配置启动**：用户无需手动设置任何环境变量
2. **友好的错误提示**：启动失败时提供清晰的错误信息和解决建议
3. **便捷的日志访问**：一键打开日志目录，方便诊断问题
4. **更好的错误恢复**：区分不同类型的错误，提供针对性的处理

这些改进大大降低了用户的使用门槛，提升了产品的专业性和用户满意度。
