/// <reference types="vite/client" />

// Electron API 类型定义
interface Window {
  electron?: {
    isElectron: boolean
    selectFolder: () => Promise<string | null>
  }
}
