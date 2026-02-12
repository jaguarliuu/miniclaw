<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useSystemInfo, type EnvironmentCheck } from '@/composables/useSystemInfo'
import { useRouter } from 'vue-router'
import ConfigCard from '@/components/common/ConfigCard.vue'

const router = useRouter()
const { systemInfo, environments, loading, refresh } = useSystemInfo()

const isRefreshing = ref(false)

const memoryUsagePercent = computed(() => {
  if (!systemInfo.value) return 0
  const used = systemInfo.value.totalMemory - systemInfo.value.freeMemory
  return Math.round((used / systemInfo.value.totalMemory) * 100)
})

const formatMemory = (bytes: number) => {
  const gb = bytes / (1024 * 1024 * 1024)
  return gb.toFixed(2) + ' GB'
}

const getStatusVariant = (installed: boolean): 'success' | 'warning' => {
  return installed ? 'success' : 'warning'
}

async function handleRefresh() {
  isRefreshing.value = true
  await refresh()
  isRefreshing.value = false
}

async function handleInstall(env: EnvironmentCheck) {
  // 跳转到工作区并触发安装对话
  const platform = systemInfo.value?.os.toLowerCase() || ''
  const isWindows = platform.includes('windows')
  const isMac = platform.includes('mac')
  const isLinux = platform.includes('linux')

  let installPrompt = ''

  if (env.name === 'Python') {
    installPrompt = generatePythonInstallPrompt(isWindows, isMac, isLinux)
  } else if (env.name === 'Node.js') {
    installPrompt = generateNodeInstallPrompt(isWindows, isMac, isLinux)
  } else if (env.name === 'Git') {
    installPrompt = generateGitInstallPrompt(isWindows, isMac, isLinux)
  }

  // 创建新会话并发送安装提示词
  router.push({
    path: '/workspace',
    query: {
      action: 'install',
      env: env.name,
      prompt: installPrompt
    }
  })
}

async function handleUninstall(env: EnvironmentCheck) {
  // 跳转到工作区并触发卸载对话
  const platform = systemInfo.value?.os.toLowerCase() || ''
  const isWindows = platform.includes('windows')
  const isMac = platform.includes('mac')
  const isLinux = platform.includes('linux')

  let uninstallPrompt = ''

  if (env.name === 'Python') {
    uninstallPrompt = generatePythonUninstallPrompt(isWindows, isMac, isLinux, env.path || '')
  } else if (env.name === 'Node.js') {
    uninstallPrompt = generateNodeUninstallPrompt(isWindows, isMac, isLinux, env.path || '')
  } else if (env.name === 'Git') {
    uninstallPrompt = generateGitUninstallPrompt(isWindows, isMac, isLinux, env.path || '')
  }

  router.push({
    path: '/workspace',
    query: {
      action: 'uninstall',
      env: env.name,
      prompt: uninstallPrompt
    }
  })
}

// 生成安装提示词
function generatePythonInstallPrompt(isWindows: boolean, isMac: boolean, isLinux: boolean): string {
  if (isWindows) {
    return `Please install Python on my Windows system using a lightweight, non-intrusive method. Requirements:
1. Use Scoop package manager (install Scoop first if not present)
2. Install Python 3.11 or latest stable version
3. Do NOT modify system PATH permanently
4. Install only in user space, not system-wide
5. Verify installation after completion

Steps:
1. Check if Scoop is installed
2. If not, install Scoop: \`irm get.scoop.sh | iex\`
3. Install Python: \`scoop install python\`
4. Verify: \`python --version\`

Important: Keep the installation isolated and easy to remove later.`
  } else if (isMac) {
    return `Please install Python on my macOS system using Homebrew. Requirements:
1. Install via Homebrew (install Homebrew first if not present)
2. Install Python 3.11 or latest stable version
3. Keep installation isolated in Homebrew prefix
4. Verify installation after completion

Steps:
1. Check if Homebrew is installed
2. If not, install Homebrew
3. Install Python: \`brew install python@3.11\`
4. Verify: \`python3 --version\`

Important: Use Homebrew for easy management and uninstallation.`
  } else if (isLinux) {
    return `Please install Python on my Linux system. Requirements:
1. Use system package manager (apt/yum/dnf)
2. Install Python 3.11 or latest available version
3. Install in standard system locations
4. Verify installation after completion

Steps:
1. Update package list
2. Install Python: \`sudo apt install python3 python3-pip\` (for Debian/Ubuntu)
   Or: \`sudo yum install python3 python3-pip\` (for RHEL/CentOS)
3. Verify: \`python3 --version\`

Important: Use system package manager for proper integration.`
  }
  return 'Install Python on this system'
}

function generateNodeInstallPrompt(isWindows: boolean, isMac: boolean, isLinux: boolean): string {
  if (isWindows) {
    return `Please install Node.js on my Windows system using Scoop. Requirements:
1. Use Scoop package manager
2. Install Node.js LTS version
3. Keep installation isolated
4. Verify installation

Steps:
1. Ensure Scoop is installed
2. Install Node.js: \`scoop install nodejs-lts\`
3. Verify: \`node --version\` and \`npm --version\`

Important: Use Scoop for easy management.`
  } else if (isMac) {
    return `Please install Node.js on my macOS system using Homebrew. Requirements:
1. Use Homebrew
2. Install Node.js LTS version
3. Verify installation

Steps:
1. Ensure Homebrew is installed
2. Install Node.js: \`brew install node@20\`
3. Verify: \`node --version\` and \`npm --version\``
  } else if (isLinux) {
    return `Please install Node.js on my Linux system. Requirements:
1. Use NodeSource repository for latest LTS
2. Install via package manager
3. Verify installation

Steps:
1. Add NodeSource repository
2. Install Node.js: \`sudo apt install nodejs\` (Debian/Ubuntu)
3. Verify: \`node --version\` and \`npm --version\``
  }
  return 'Install Node.js on this system'
}

function generateGitInstallPrompt(isWindows: boolean, isMac: boolean, isLinux: boolean): string {
  if (isWindows) {
    return `Please install Git on my Windows system using Scoop. Requirements:
1. Use Scoop package manager
2. Install latest Git version
3. Keep installation isolated
4. Verify installation

Steps:
1. Ensure Scoop is installed
2. Install Git: \`scoop install git\`
3. Verify: \`git --version\`

Important: Scoop keeps Git isolated and easy to manage.`
  } else if (isMac) {
    return `Please install Git on my macOS system. Requirements:
1. Git may already be available via Xcode Command Line Tools
2. If not, use Homebrew
3. Verify installation

Steps:
1. Check: \`git --version\` (may trigger Xcode CLT install)
2. Or install via Homebrew: \`brew install git\`
3. Verify: \`git --version\``
  } else if (isLinux) {
    return `Please install Git on my Linux system. Requirements:
1. Use system package manager
2. Install latest available version
3. Verify installation

Steps:
1. Install Git: \`sudo apt install git\` (Debian/Ubuntu)
   Or: \`sudo yum install git\` (RHEL/CentOS)
2. Verify: \`git --version\``
  }
  return 'Install Git on this system'
}

// 生成卸载提示词
function generatePythonUninstallPrompt(isWindows: boolean, isMac: boolean, isLinux: boolean, path: string): string {
  if (isWindows) {
    return `Please uninstall Python from my Windows system. Current installation path: ${path}

Requirements:
1. If installed via Scoop: \`scoop uninstall python\`
2. Clean up any remaining files
3. Verify removal: \`python --version\` should fail

Important: Only remove Scoop-installed Python, not system Python if exists.`
  } else if (isMac) {
    return `Please uninstall Python from my macOS system. Current installation path: ${path}

Requirements:
1. If installed via Homebrew: \`brew uninstall python@3.11\`
2. Verify removal: \`python3 --version\` should show system Python or fail

Important: Only remove Homebrew-installed Python.`
  } else if (isLinux) {
    return `Please uninstall Python from my Linux system. Current installation path: ${path}

Requirements:
1. Use package manager: \`sudo apt remove python3\` (Debian/Ubuntu)
2. Verify removal

Warning: This may affect system dependencies. Proceed with caution.`
  }
  return 'Uninstall Python from this system'
}

function generateNodeUninstallPrompt(isWindows: boolean, isMac: boolean, isLinux: boolean, path: string): string {
  if (isWindows) {
    return `Please uninstall Node.js from my Windows system. Current installation path: ${path}

Steps:
1. If installed via Scoop: \`scoop uninstall nodejs-lts\`
2. Verify removal: \`node --version\` should fail`
  } else if (isMac) {
    return `Please uninstall Node.js from my macOS system. Current installation path: ${path}

Steps:
1. If installed via Homebrew: \`brew uninstall node\`
2. Verify removal`
  } else if (isLinux) {
    return `Please uninstall Node.js from my Linux system. Current installation path: ${path}

Steps:
1. Use package manager: \`sudo apt remove nodejs\`
2. Verify removal`
  }
  return 'Uninstall Node.js from this system'
}

function generateGitUninstallPrompt(isWindows: boolean, isMac: boolean, isLinux: boolean, path: string): string {
  if (isWindows) {
    return `Please uninstall Git from my Windows system. Current installation path: ${path}

Steps:
1. If installed via Scoop: \`scoop uninstall git\`
2. Verify removal: \`git --version\` should fail`
  } else if (isMac) {
    return `Please uninstall Git from my macOS system. Current installation path: ${path}

Note: If installed via Xcode CLT, it cannot be easily removed.
If installed via Homebrew: \`brew uninstall git\``
  } else if (isLinux) {
    return `Please uninstall Git from my Linux system. Current installation path: ${path}

Steps:
1. Use package manager: \`sudo apt remove git\`
2. Verify removal`
  }
  return 'Uninstall Git from this system'
}

onMounted(() => {
  refresh()
})
</script>

<template>
  <div class="system-section">
    <header class="section-header">
      <div>
        <h2 class="section-title">System</h2>
        <p class="section-subtitle">System information and environment management</p>
      </div>
      <button class="refresh-btn" @click="handleRefresh" :disabled="isRefreshing">
        <span class="refresh-icon" :class="{ spinning: isRefreshing }">↻</span>
        Refresh
      </button>
    </header>

    <div v-if="loading && !systemInfo" class="loading-state">Loading system information...</div>

    <template v-if="systemInfo">
      <!-- System Info Card -->
      <div class="info-card">
        <div class="info-header">
          <h3 class="info-title">System Information</h3>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">Operating System</span>
            <span class="info-value">{{ systemInfo.os }} {{ systemInfo.osVersion }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">Architecture</span>
            <span class="info-value">{{ systemInfo.architecture }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">Java Version</span>
            <span class="info-value">{{ systemInfo.javaVersion }} ({{ systemInfo.javaVendor }})</span>
          </div>
          <div class="info-item">
            <span class="info-label">CPU Cores</span>
            <span class="info-value">{{ systemInfo.availableProcessors }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">Memory Usage</span>
            <span class="info-value">
              {{ formatMemory(systemInfo.totalMemory - systemInfo.freeMemory) }} /
              {{ formatMemory(systemInfo.totalMemory) }}
              ({{ memoryUsagePercent }}%)
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">User</span>
            <span class="info-value">{{ systemInfo.userName }}</span>
          </div>
        </div>
      </div>

      <!-- Environment Status -->
      <div class="section-divider">
        <h3 class="divider-title">Development Environment</h3>
        <p class="divider-subtitle">Manage Python, Node.js, and Git installations</p>
      </div>

      <div class="env-grid">
        <div v-for="env in environments" :key="env.name" class="env-card">
          <div class="env-header">
            <div class="env-info">
              <div class="env-details">
                <h4 class="env-name">{{ env.name }}</h4>
                <p v-if="env.installed" class="env-version">{{ env.version }}</p>
                <p v-else class="env-not-installed">Not installed</p>
              </div>
            </div>
            <span
              class="env-badge"
              :class="`badge-${getStatusVariant(env.installed)}`"
            >
              {{ env.installed ? 'Installed' : 'Not Found' }}
            </span>
          </div>

          <div v-if="env.installed && env.path" class="env-path">
            <span class="path-label">Path:</span>
            <span class="path-value">{{ env.path }}</span>
          </div>

          <div class="env-actions">
            <button
              v-if="!env.installed"
              class="btn-install"
              @click="handleInstall(env)"
            >
              Install {{ env.name }}
            </button>
            <button
              v-else
              class="btn-uninstall"
              @click="handleUninstall(env)"
            >
              Uninstall
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.system-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}

.section-title {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 500;
  margin-bottom: 4px;
}

.section-subtitle {
  font-size: 14px;
  color: var(--color-gray-dark);
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.refresh-btn:hover:not(:disabled) {
  background: var(--color-gray-bg);
  border-color: var(--color-black);
}

.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.refresh-icon {
  font-size: 16px;
  transition: transform 0.6s ease;
}

.refresh-icon.spinning {
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.loading-state {
  padding: 40px;
  text-align: center;
  color: var(--color-gray-dark);
}

.info-card {
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-lg);
  padding: 20px;
  margin-bottom: 24px;
}

.info-header {
  margin-bottom: 16px;
}

.info-title {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-600);
}

.info-value {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-black);
}

.section-divider {
  margin: 32px 0 20px 0;
}

.divider-title {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px 0;
}

.divider-subtitle {
  font-size: 13px;
  color: var(--color-gray-600);
  margin: 0;
}

.env-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.env-card {
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-lg);
  padding: 20px;
  transition: all var(--duration-fast);
}

.env-card:hover {
  border-color: var(--color-gray-400);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.env-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.env-info {
  display: flex;
  align-items: flex-start;
}

.env-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.env-name {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}

.env-version {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-600);
  margin: 0;
}

.env-not-installed {
  font-size: 12px;
  color: var(--color-gray-500);
  font-style: italic;
  margin: 0;
}

.env-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  padding: 3px 8px;
  border-radius: var(--radius-sm);
}

.badge-success {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

.badge-warning {
  background: #fefce8;
  border: 1px solid #fde68a;
  color: #92400e;
}

.env-path {
  margin-bottom: 12px;
  padding: 8px;
  background: var(--color-gray-50);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-700);
  word-break: break-all;
}

.path-label {
  font-weight: 600;
  margin-right: 6px;
}

.env-actions {
  display: flex;
  gap: 8px;
}

.btn-install {
  flex: 1;
  padding: 8px 14px;
  background: var(--color-black);
  color: var(--color-white);
  border: none;
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-install:hover {
  background: var(--color-gray-800);
}

.btn-uninstall {
  flex: 1;
  padding: 8px 14px;
  background: var(--color-white);
  color: var(--color-red-600);
  border: 1px solid var(--color-red-200);
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-uninstall:hover {
  background: var(--color-red-50);
  border-color: var(--color-red-300);
}
</style>
