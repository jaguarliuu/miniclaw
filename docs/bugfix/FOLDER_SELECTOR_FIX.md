# 文件夹选择功能修正说明

## 问题

在 Web 浏览器环境中，使用 `webkitdirectory` 属性会触发浏览器的文件上传行为，用户选择文件夹后，浏览器会尝试上传文件夹中的所有文件。这不符合我们的需求——我们只需要文件夹路径，而不需要上传文件。

## 根本原因

Web 浏览器出于安全考虑，**不允许 JavaScript 直接访问本地文件系统路径**。`webkitdirectory` 属性的设计初衷是用于文件上传，而不是路径获取。因此：

- ✅ **Electron 环境**：可以通过 `dialog.showOpenDialog` 获取本地路径
- ❌ **Web 浏览器环境**：无法获取本地文件系统的绝对路径

## 解决方案

修改实现策略，区分两种环境：

### 1. Electron 桌面环境
- ✅ 显示 "📁 Browse" 按钮
- ✅ 使用系统原生文件夹选择对话框
- ✅ 返回绝对路径（如 `/Users/john/workspace/src`）

### 2. Web 浏览器环境
- ❌ **不显示** Browse 按钮
- ✅ 只支持手动输入路径
- ✅ 显示输入提示，引导用户输入正确格式的路径

## 代码修改

### 修改 1：删除 Web 环境的文件夹选择逻辑

**删除的代码**：
```typescript
// 删除：Web 环境的文件夹选择 ref
const folderInputRef = ref<HTMLInputElement | null>(null)

// 删除：handleWebFolderSelect 函数
function handleWebFolderSelect(e: Event) {
  // ...
}
```

**修改后的 handleSelectFolder**：
```typescript
async function handleSelectFolder() {
  if (!isElectron.value) {
    // Web 环境不支持图形化文件夹选择，只能手动输入
    return
  }

  try {
    const folderPath = await (window as any).electron.selectFolder()
    if (folderPath) {
      inputValue.value = folderPath
      errorMessage.value = ''
    }
  } catch (err) {
    errorMessage.value = 'Failed to select folder'
    console.error('Failed to select folder:', err)
  }
}
```

### 修改 2：条件渲染 Browse 按钮

**修改后的模板**：
```vue
<div v-if="type === 'folder'" class="folder-input-group">
  <input
    ref="inputRef"
    v-model="inputValue"
    type="text"
    :placeholder="placeholder"
    class="modal-input"
    :class="{ error: errorMessage }"
    @keydown="handleKeydown"
    @input="handleInput"
  />
  <!-- 只在 Electron 环境显示 Browse 按钮 -->
  <button
    v-if="isElectron"
    class="browse-btn"
    @click="handleSelectFolder"
    title="Browse folder"
  >
    📁 Browse
  </button>
</div>

<!-- Web 环境的提示 -->
<div v-if="type === 'folder' && !isElectron" class="hint-message">
  💡 Tip: Enter a relative path like "workspace/src" or an absolute path
</div>
```

### 修改 3：添加提示样式

```css
.hint-message {
  margin-top: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-500);
  line-height: 1.4;
}
```

## 用户体验

### Electron 桌面环境

用户看到的界面：
```
┌────────────────────────────────────────┐
│  Add Folder Path                    ×  │
├────────────────────────────────────────┤
│                                        │
│  ┌─────────────────────┬────────────┐ │
│  │ workspace/src       │ 📁 Browse │ │
│  └─────────────────────┴────────────┘ │
│         ↑                    ↑         │
│   可手动输入          或点击浏览按钮   │
│                                        │
│  ┌────────┐  ┌────────┐               │
│  │ Cancel │  │  Add   │               │
│  └────────┘  └────────┘               │
└────────────────────────────────────────┘
```

### Web 浏览器环境

用户看到的界面：
```
┌────────────────────────────────────────┐
│  Add Folder Path                    ×  │
├────────────────────────────────────────┤
│                                        │
│  ┌──────────────────────────────────┐ │
│  │ workspace/src                    │ │
│  └──────────────────────────────────┘ │
│         ↑                              │
│   只能手动输入                          │
│                                        │
│  💡 Tip: Enter a relative path like   │
│     "workspace/src" or an absolute    │
│     path                               │
│                                        │
│  ┌────────┐  ┌────────┐               │
│  │ Cancel │  │  Add   │               │
│  └────────┘  └────────┘               │
└────────────────────────────────────────┘
```

## 路径格式说明

### 推荐使用相对路径（适用于所有环境）

相对路径相对于 `workspace` 目录：

```
workspace/src
workspace/uploads
workspace/memory
data/config
```

**优点**：
- ✅ 跨平台兼容
- ✅ 便于分享和协作
- ✅ 不依赖本地文件系统结构

### 绝对路径（仅 Electron 环境推荐）

在 Electron 环境中选择文件夹后会自动填充绝对路径：

**macOS / Linux**：
```
/Users/john/Documents/MyProject
/home/john/projects/app
```

**Windows**：
```
C:\Users\John\Documents\MyProject
D:\Projects\App
```

**注意**：
- Web 环境也可以输入绝对路径，但 Agent 无法访问本地文件系统
- 绝对路径不便于跨设备使用

## 安全说明

### 为什么 Web 环境不能获取本地路径？

这是浏览器的安全限制，防止恶意网站：
1. 探测用户的文件系统结构
2. 窃取敏感文件路径信息
3. 未经授权访问本地文件

### Agent 如何访问文件夹？

无论是相对路径还是绝对路径，Agent 都通过以下工具访问：
- `glob` - 列出文件夹中的文件
- `grep` - 搜索文件夹中的内容
- `read_file` - 读取具体文件

这些工具在后端运行，受沙箱限制保护。

## 使用建议

### Web 环境用户

1. **使用相对路径**（推荐）：
   ```
   workspace/src
   workspace/tests
   ```

2. **使用工作区子目录**：
   - 确保路径在 `workspace` 目录下
   - 这样 Agent 才有权限访问

3. **避免绝对路径**：
   - Web 环境中的绝对路径 Agent 无法访问
   - 除非该路径映射到服务器端的工作区

### Electron 环境用户

1. **使用 Browse 按钮**（推荐）：
   - 点击 📁 Browse 选择文件夹
   - 自动填充正确的绝对路径

2. **或手动输入相对路径**：
   - 更便于跨设备使用
   - 便于与团队分享会话

## 常见问题

### Q1: 为什么 Web 环境没有 Browse 按钮？

**答**：浏览器出于安全考虑，不允许 JavaScript 获取本地文件系统路径。Web 环境只能手动输入路径。

### Q2: Web 环境输入的路径 Agent 能访问吗？

**答**：
- ✅ 相对于 `workspace` 的路径可以访问（如 `workspace/src`）
- ❌ 本地文件系统的绝对路径无法访问（如 `C:\Users\...`）

### Q3: 可以在 Web 环境中上传整个文件夹吗？

**答**：不建议。我们的设计目标是让 Agent 通过工具（glob/grep/read_file）探索文件夹，而不是上传所有文件。上传会：
- 消耗大量带宽
- 占用服务器存储空间
- 增加安全风险

### Q4: Electron 环境选择的绝对路径在其他设备上能用吗？

**答**：不能。绝对路径是设备特定的。如果需要跨设备使用，建议：
1. 将文件复制到 `workspace` 目录
2. 使用相对路径（如 `workspace/myproject`）

## 验证结果

- ✅ TypeScript 类型检查通过
- ✅ Electron 环境：Browse 按钮正常工作
- ✅ Web 环境：无 Browse 按钮，显示提示信息
- ✅ 两种环境都支持手动输入路径

## 总结

通过这次修正：

1. **解决了 Web 环境的文件上传问题**：不再触发不必要的文件上传
2. **明确了环境差异**：Electron 有 Browse 按钮，Web 只能手动输入
3. **提供了清晰的用户指引**：Web 环境显示提示信息
4. **保持了功能一致性**：两种环境都能添加文件夹上下文

用户现在可以根据环境选择最合适的方式添加文件夹路径，不会再遇到意外的文件上传行为。🎉
