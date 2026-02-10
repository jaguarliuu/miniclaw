# 文件夹选择器功能实现总结

## 问题描述

在添加 Folder 上下文时，原有设计要求用户手动输入文件夹路径，这种体验很不好。用户期望能够通过图形界面选择文件夹，就像选择文件一样方便。

## 解决方案

实现了一个跨平台的文件夹选择器，同时支持：
1. **Electron 桌面环境**：使用 Electron 的 dialog API 提供原生文件夹选择对话框
2. **Web 浏览器环境**：使用 HTML5 的 `webkitdirectory` 属性实现文件夹选择

## 实现详情

### 1. Electron Preload API 暴露

**文件**: `electron/preload.js`

使用 `contextBridge` 安全地暴露文件夹选择 API 给渲染进程：

```javascript
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electron', {
  // 选择文件夹
  selectFolder: () => ipcRenderer.invoke('dialog:selectFolder'),

  // 检查是否在 Electron 环境中
  isElectron: true
});
```

**安全性**：
- ✅ 使用 `contextBridge` 而不是直接暴露 Node.js API
- ✅ 保持 `contextIsolation` 启用
- ✅ 只暴露必要的功能，不暴露整个 `ipcRenderer`

### 2. Electron 主进程 IPC 处理

**文件**: `electron/main.js`

添加 IPC handler 处理文件夹选择请求：

```javascript
const { app, BrowserWindow, dialog, ipcMain } = require('electron');

// IPC Handlers
ipcMain.handle('dialog:selectFolder', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory']
  });

  if (result.canceled) {
    return null;
  }

  // 返回选中的文件夹路径
  return result.filePaths[0];
});
```

**特性**：
- 使用 `ipcMain.handle` 而不是 `ipcMain.on`，支持异步返回结果
- 只允许选择文件夹（`openDirectory`）
- 返回绝对路径

### 3. TypeScript 类型定义

**文件**: `miniclaw-ui/env.d.ts`

为 `window.electron` 添加类型定义：

```typescript
interface Window {
  electron?: {
    isElectron: boolean
    selectFolder: () => Promise<string | null>
  }
}
```

**好处**：
- ✅ 提供类型安全
- ✅ IDE 自动完成
- ✅ 编译时类型检查

### 4. ContextInputModal 组件改造

**文件**: `miniclaw-ui/src/components/ContextInputModal.vue`

#### 环境检测
```typescript
// 检查是否在 Electron 环境中
const isElectron = computed(() => {
  return typeof window !== 'undefined' && (window as any).electron?.isElectron
})
```

#### Electron 环境的文件夹选择
```typescript
async function handleSelectFolder() {
  if (!isElectron.value) {
    // Web 环境，使用 HTML5 文件夹选择
    folderInputRef.value?.click()
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

#### Web 环境的文件夹选择
```typescript
function handleWebFolderSelect(e: Event) {
  const target = e.target as HTMLInputElement
  const files = target.files
  if (files && files.length > 0) {
    const firstFile = files[0]
    if (firstFile) {
      const relativePath = (firstFile as any).webkitRelativePath || firstFile.name
      const folderName = relativePath.split('/')[0]
      inputValue.value = folderName
      errorMessage.value = ''
    }
  }
  target.value = ''
}
```

#### UI 模板
```vue
<!-- 文件夹类型：显示输入框和浏览按钮 -->
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
  <button class="browse-btn" @click="handleSelectFolder" title="Browse folder">
    📁 Browse
  </button>
  <!-- 隐藏的文件夹选择 input (Web 环境) -->
  <input
    ref="folderInputRef"
    type="file"
    webkitdirectory
    directory
    multiple
    style="display: none"
    @change="handleWebFolderSelect"
  />
</div>
```

#### CSS 样式
```css
.folder-input-group {
  display: flex;
  gap: 8px;
  align-items: stretch;
}

.browse-btn {
  padding: 10px 16px;
  border: var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--color-white);
  color: var(--color-gray-700);
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}

.browse-btn:hover {
  background: var(--color-gray-50);
  border-color: var(--color-gray-400);
}
```

## 用户体验

### 改进前 ❌
1. 用户需要手动输入文件夹路径
2. 容易输入错误的路径
3. 不直观，需要记住路径格式
4. Windows 和 Mac 路径格式不同，容易混淆

### 改进后 ✅
1. **Electron 桌面版**：
   - 点击 "📁 Browse" 按钮
   - 打开系统原生的文件夹选择对话框
   - 选择文件夹后自动填充到输入框
   - 支持绝对路径

2. **Web 浏览器版**：
   - 点击 "📁 Browse" 按钮
   - 触发 HTML5 文件夹选择器
   - 选择文件夹后提取文件夹名称
   - 支持相对路径

3. **手动输入**：
   - 仍然保留输入框，支持手动输入或编辑路径
   - 适合高级用户或需要输入特定路径的场景

## 技术细节

### Electron 环境检测

通过检查 `window.electron` 对象的存在来判断是否在 Electron 环境中运行：

```typescript
const isElectron = computed(() => {
  return typeof window !== 'undefined' && (window as any).electron?.isElectron
})
```

这种方式比检查 `navigator.userAgent` 更可靠，因为：
- ✅ 不依赖 User-Agent 字符串
- ✅ 明确检查 preload 脚本暴露的 API
- ✅ 避免误判（某些浏览器可能包含 "Electron" 字符串）

### Web 环境的文件夹选择

使用 HTML5 的 `webkitdirectory` 和 `directory` 属性：

```html
<input
  type="file"
  webkitdirectory
  directory
  multiple
  @change="handleWebFolderSelect"
/>
```

**注意**：
- `webkitdirectory` 是非标准属性，但被大多数现代浏览器支持
- `directory` 是标准属性，但支持度较低
- 同时设置两个属性以提高兼容性
- `multiple` 属性必须设置，否则无法选择文件夹

### 路径提取

Web 环境中，从 `webkitRelativePath` 提取文件夹名：

```typescript
const relativePath = (firstFile as any).webkitRelativePath || firstFile.name
const folderName = relativePath.split('/')[0]
```

**示例**：
- 选择文件夹 `/Users/john/Documents/MyProject`
- 选择的第一个文件可能是 `MyProject/src/index.js`
- `webkitRelativePath` 返回 `MyProject/src/index.js`
- 提取第一个 `/` 之前的部分：`MyProject`

## 浏览器兼容性

### webkitdirectory 支持情况

| 浏览器 | 版本 | 支持 |
|--------|------|------|
| Chrome | 21+ | ✅ |
| Edge | 13+ | ✅ |
| Firefox | 50+ | ✅ |
| Safari | 11.1+ | ✅ |
| Opera | 15+ | ✅ |

### Electron 环境

| Electron 版本 | 支持 |
|---------------|------|
| 1.0+ | ✅ |

所有现代版本的 Electron 都支持 `dialog.showOpenDialog`。

## 安全考虑

### 1. 路径验证

仍然保留路径验证，防止恶意路径：

```typescript
if (trimmed.includes('..')) {
  return 'Path cannot contain ".."'
}
```

### 2. 沙箱隔离

Electron 环境中：
- ✅ `contextIsolation` 启用
- ✅ 使用 `contextBridge` 暴露有限的 API
- ✅ 不直接暴露文件系统访问

### 3. 权限控制

Web 环境中：
- ✅ 文件夹选择由浏览器控制，用户需要主动授权
- ✅ 只能读取文件夹结构，不能直接访问文件内容

## 测试建议

### 场景 1: Electron 环境 - 选择文件夹
1. 启动 Electron 桌面应用
2. 点击 + 按钮 → 选择 Folder
3. 点击 "📁 Browse" 按钮
4. 在系统对话框中选择一个文件夹
5. 预期结果：文件夹路径自动填充到输入框

### 场景 2: Electron 环境 - 手动输入
1. 启动 Electron 桌面应用
2. 点击 + 按钮 → 选择 Folder
3. 直接在输入框中输入路径（如 `workspace/src`）
4. 点击 Add
5. 预期结果：成功添加 Folder 上下文

### 场景 3: Web 环境 - 选择文件夹
1. 在浏览器中打开应用
2. 点击 + 按钮 → 选择 Folder
3. 点击 "📁 Browse" 按钮
4. 在浏览器的文件夹选择器中选择一个文件夹
5. 预期结果：文件夹名称自动填充到输入框

### 场景 4: Web 环境 - 手动输入
1. 在浏览器中打开应用
2. 点击 + 按钮 → 选择 Folder
3. 直接在输入框中输入路径
4. 点击 Add
5. 预期结果：成功添加 Folder 上下文

### 场景 5: 路径验证
1. 点击 + 按钮 → 选择 Folder
2. 输入包含 `..` 的路径（如 `workspace/../etc`）
3. 点击 Add
4. 预期结果：显示错误提示 "Path cannot contain .."

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `electron/preload.js` | 修改 | 暴露文件夹选择 API |
| `electron/main.js` | 修改 | 添加 IPC handler 处理文件夹选择 |
| `miniclaw-ui/env.d.ts` | 修改 | 添加 window.electron 类型定义 |
| `miniclaw-ui/src/components/ContextInputModal.vue` | 修改 | 添加文件夹选择按钮和逻辑 |

## 验证结果

- ✅ JavaScript 语法检查通过
- ✅ TypeScript 类型检查通过
- ✅ 支持 Electron 和 Web 双环境
- ✅ 保持向后兼容（仍支持手动输入）

## 后续改进建议

1. **记住上次选择的文件夹**：保存到本地存储，下次打开时默认到上次的位置
2. **文件夹路径预览**：在输入框下方显示绝对路径预览
3. **快捷路径**：提供常用路径的快捷选择（workspace、home、desktop 等）
4. **拖拽支持**：允许直接拖拽文件夹到输入框
5. **路径自动补全**：输入时提供路径自动补全建议

## 总结

通过这个改进，文件夹选择体验得到了显著提升：

1. **更直观**：用户可以通过图形界面浏览和选择文件夹
2. **更准确**：避免手动输入路径时的拼写错误
3. **更方便**：一键选择，无需记住路径格式
4. **跨平台**：同时支持 Electron 和 Web 环境
5. **灵活性**：仍然保留手动输入选项，满足高级用户需求

这个实现充分考虑了安全性、兼容性和用户体验，是一个完整的跨平台解决方案。🎉
