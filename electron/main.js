const { app, BrowserWindow, dialog } = require('electron');
const { spawn, execSync } = require('child_process');
const path = require('path');
const http = require('http');
const portfinder = require('portfinder');
const { autoUpdater } = require('electron-updater');

let mainWindow = null;
let splashWindow = null;
let javaProcess = null;
let serverPort = null;

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

function ensureDirectories() {
  const fs = require('fs');
  for (const dir of [appDataPath, dataDir, workspacePath]) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

function createSplashWindow() {
  splashWindow = new BrowserWindow({
    width: 400,
    height: 300,
    frame: false,
    resizable: false,
    transparent: false,
    alwaysOnTop: true,
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

function startJavaBackend(port) {
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
  ];

  console.log(`Starting Java: ${javaExe} ${args.join(' ')}`);

  javaProcess = spawn(javaExe, args, {
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });

  javaProcess.stdout.on('data', (data) => {
    process.stdout.write(`[Java] ${data}`);
  });

  javaProcess.stderr.on('data', (data) => {
    process.stderr.write(`[Java] ${data}`);
  });

  javaProcess.on('exit', (code) => {
    console.log(`Java process exited with code ${code}`);
    javaProcess = null;
    // If Java crashes unexpectedly while window is open, quit the app
    if (mainWindow && !mainWindow.isDestroyed()) {
      app.quit();
    }
  });
}

function waitForHealth(port, timeoutMs = 60000) {
  const startTime = Date.now();
  const interval = 1000;

  return new Promise((resolve, reject) => {
    function check() {
      if (Date.now() - startTime > timeoutMs) {
        return reject(new Error('Backend health check timed out'));
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

app.whenReady().then(async () => {
  try {
    ensureDirectories();
    createSplashWindow();

    // Find available port starting from 18080
    portfinder.basePort = 18080;
    serverPort = await portfinder.getPortPromise();
    console.log(`Using port: ${serverPort}`);

    // Start Java backend
    startJavaBackend(serverPort);

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
    killJavaProcess();
    app.quit();
  }
});

app.on('window-all-closed', () => {
  killJavaProcess();
  app.quit();
});

app.on('before-quit', () => {
  killJavaProcess();
});
