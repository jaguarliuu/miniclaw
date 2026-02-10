const { contextBridge, ipcRenderer } = require('electron');

// 暴露安全的 API 给渲染进程
contextBridge.exposeInMainWorld('electron', {
  // 选择文件夹
  selectFolder: () => ipcRenderer.invoke('dialog:selectFolder'),

  // 检查是否在 Electron 环境中
  isElectron: true
});

