const { app, BrowserWindow, dialog, ipcMain } = require('electron');
const { spawn, execSync } = require('child_process');
const path = require('path');
const http = require('http');
const portfinder = require('portfinder');
const { autoUpdater } = require('electron-updater');
const crypto = require('crypto');
const fs = require('fs');

let mainWindow = null;
let splashWindow = null;
let javaProcess = null;
let serverPort = null;
let javaErrorLogs = [];  // 收集 Java 错误日志

// Paths
const isPackaged = app.isPackaged;
const resourcesPath = isPackaged
  ? path.join(process.resourcesPath)
  : path.join(__dirname, 'resources');

const jrePath = path.join(resourcesPath, 'jre');
const jarPath = path.join(resourcesPath, 'app.jar');
const webappPath = path.join(resourcesPath, 'webapp');
const appDataPath = path.join(app.getPath('appData'), 'MiniClaw');

const dataDir = path.join(appDataPath, 'data');
const dbPath = path.join(appDataPath, 'miniclaw.db');
const workspacePath = path.join(appDataPath, 'workspace');
const skillsDir = path.join(appDataPath, 'skills');
const builtinSkillsDir = path.join(resourcesPath, 'skills');
const configPath = path.join(appDataPath, 'config.json');

function ensureDirectories() {
  for (const dir of [appDataPath, dataDir, workspacePath, skillsDir]) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

/**
 * 获取或生成加密密钥
 * 如果配置文件中没有密钥，自动生成一个 64 字符的 hex 密钥（32 字节）
 */
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

function createSplashWindow() {
  splashWindow = new BrowserWindow({
    width: 400,
    height: 300,
    frame: false,
    resizable: false,
    transparent: false,
    alwaysOnTop: true,
    icon: path.join(resourcesPath, 'icon.png'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  splashWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(`
    <!DOCTYPE html>
    <html>
    <head>
      <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
          display: flex; flex-direction: column;
          align-items: center; justify-content: center;
          height: 100vh;
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          color: white;
          -webkit-app-region: drag;
        }
        h1 { font-size: 32px; margin-bottom: 16px; font-weight: 300; }
        .spinner {
          width: 40px; height: 40px;
          border: 3px solid rgba(255,255,255,0.3);
          border-top-color: white;
          border-radius: 50%;
          animation: spin 0.8s linear infinite;
          margin-bottom: 16px;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        p { font-size: 14px; opacity: 0.8; }
      </style>
    </head>
    <body>
      <h1>MiniClaw</h1>
      <div class="spinner"></div>
      <p>Starting backend server...</p>
    </body>
    </html>
  `)}`);
}

function startJavaBackend(port, encryptionKey) {
  const javaBin = process.platform === 'win32' ? 'java.exe' : 'java';
  const javaExe = path.join(jrePath, 'bin', javaBin);

  const args = [
    '-jar', jarPath,
    `--spring.profiles.active=sqlite`,
    `--server.port=${port}`,
    `--miniclaw.config-dir=${dataDir}`,
    `--miniclaw.webapp-dir=${webappPath}`,
    `--spring.datasource.url=jdbc:sqlite:${dbPath}`,
    `--tools.workspace=${workspacePath}`,
    `--skills.user-dir=${skillsDir}`,
    `--skills.builtin-dir=${builtinSkillsDir}`,
  ];

  // 设置环境变量
  const env = {
    ...process.env,
    NODE_CONSOLE_ENCRYPTION_KEY: encryptionKey
  };

  console.log(`Starting Java: ${javaExe} ${args.join(' ')}`);
  console.log('Encryption key configured');

  javaProcess = spawn(javaExe, args, {
    env: env,
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });

  javaProcess.stdout.on('data', (data) => {
    process.stdout.write(`[Java] ${data}`);
  });

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
    } else if (hadProcess && !mainWindow) {
      // Java 进程在主窗口创建之前退出（启动阶段失败）
      // 这种情况已经在 waitForHealth 中处理了，这里不需要额外操作
      console.log('Java process exited during startup phase');
    }
  });
}

/**
 * 显示启动错误对话框
 * @param {string} title - 错误标题
 * @param {string} message - 错误消息
 * @param {string} details - 详细错误信息（可选）
 */
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

/**
 * 从错误日志中提取关键错误信息
 */
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

function waitForHealth(port, timeoutMs = 60000) {
  const startTime = Date.now();
  const interval = 1000;

  return new Promise((resolve, reject) => {
    function check() {
      const elapsed = Date.now() - startTime;
      if (elapsed > timeoutMs) {
        const errorMsg = extractErrorMessage();
        return reject(new Error(`Backend health check timed out after ${Math.floor(elapsed / 1000)}s.\n\nLast logs:\n${errorMsg}`));
      }

      const req = http.get(`http://localhost:${port}/actuator/health`, (res) => {
        if (res.statusCode === 200) {
          resolve();
        } else {
          setTimeout(check, interval);
        }
      });

      req.on('error', () => {
        setTimeout(check, interval);
      });

      req.setTimeout(2000, () => {
        req.destroy();
        setTimeout(check, interval);
      });
    }

    check();
  });
}

function createMainWindow(port) {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    title: 'MiniClaw',
    icon: path.join(resourcesPath, 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  mainWindow.loadURL(`http://localhost:${port}`);

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function killJavaProcess() {
  if (!javaProcess) return;

  try {
    // Windows: use taskkill to kill the process tree
    execSync(`taskkill /pid ${javaProcess.pid} /f /t`, { stdio: 'ignore' });
  } catch {
    // Fallback: try SIGTERM
    try {
      javaProcess.kill('SIGTERM');
    } catch {
      // Process already dead
    }
  }
  javaProcess = null;
}

function setupAutoUpdater() {
  if (!app.isPackaged) return;

  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = true;

  autoUpdater.on('update-available', (info) => {
    dialog
      .showMessageBox(mainWindow, {
        type: 'info',
        title: 'Update Available',
        message: `A new version ${info.version} is available. Download now?`,
        buttons: ['Download', 'Later'],
        defaultId: 0,
        cancelId: 1,
      })
      .then(({ response }) => {
        if (response === 0) {
          autoUpdater.downloadUpdate();
        }
      });
  });

  autoUpdater.on('update-downloaded', () => {
    dialog
      .showMessageBox(mainWindow, {
        type: 'info',
        title: 'Update Ready',
        message: 'Update downloaded. It will be installed when you close the app.',
        buttons: ['Restart Now', 'Later'],
        defaultId: 0,
        cancelId: 1,
      })
      .then(({ response }) => {
        if (response === 0) {
          autoUpdater.quitAndInstall();
        }
      });
  });

  autoUpdater.on('error', (err) => {
    console.log('Auto-updater error:', err.message);
  });

  setTimeout(() => {
    autoUpdater.checkForUpdates();
  }, 5000);
}

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

app.whenReady().then(async () => {
  try {
    // 确保目录存在
    ensureDirectories();

    // 获取或生成加密密钥
    const encryptionKey = getOrCreateEncryptionKey();

    // 创建启动页面
    createSplashWindow();

    // Find available port starting from 18080
    portfinder.basePort = 18080;
    serverPort = await portfinder.getPortPromise();
    console.log(`Using port: ${serverPort}`);

    // 重置错误日志
    javaErrorLogs = [];

    // Start Java backend with encryption key
    startJavaBackend(serverPort, encryptionKey);

    // Wait for backend to be ready
    await waitForHealth(serverPort);

    // Create main window
    createMainWindow(serverPort);

    // Close splash
    if (splashWindow && !splashWindow.isDestroyed()) {
      splashWindow.close();
      splashWindow = null;
    }

    // Check for updates
    setupAutoUpdater();
  } catch (err) {
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
});

app.on('window-all-closed', () => {
  killJavaProcess();
  app.quit();
});

app.on('before-quit', () => {
  killJavaProcess();
});
