# agent-browser Installation Scripts

These scripts automatically detect and install agent-browser using the best available method for your system.

## Usage

### macOS/Linux

```bash
bash scripts/install-agent-browser.sh
```

### Windows

```powershell
powershell scripts/install-agent-browser.ps1
```

## What the scripts do

1. **Check if already installed** - If agent-browser is found, shows version and exits
2. **Try npm** (Recommended) - Installs via `npm install -g @agent-tools/browser`
3. **Try platform-specific package managers**:
   - macOS: Homebrew (`brew install`)
   - Windows: Chocolatey (`choco install`)
4. **Provide fallback instructions** - If no package manager found, shows installation guide

## Manual Installation

If the scripts don't work, install manually:

### Via npm (all platforms)

```bash
npm install -g @agent-tools/browser
```

### Via package managers

**macOS:**
```bash
# Using Homebrew (when available)
brew install agent-browser

# Or using npm
npm install -g @agent-tools/browser
```

**Windows:**
```powershell
# Using Chocolatey (when available)
choco install agent-browser

# Or using npm
npm install -g @agent-tools/browser
```

**Linux:**
```bash
# Using npm
npm install -g @agent-tools/browser
```

## Troubleshooting

### "command not found: agent-browser"

**After installing via npm:**
- Close and reopen your terminal
- Or reload your shell config: `source ~/.bashrc` (or `~/.zshrc`)
- Windows: Run `refreshenv` or restart terminal

**Check npm global bin path:**
```bash
npm config get prefix
```

Add this to your PATH if needed:
- Linux/Mac: Add to `~/.bashrc` or `~/.zshrc`:
  ```bash
  export PATH="$PATH:$(npm config get prefix)/bin"
  ```
- Windows: Add `%APPDATA%\npm` to system PATH

### "npm: command not found"

Install Node.js first:
- **macOS**: `brew install node` or download from https://nodejs.org/
- **Windows**: Download from https://nodejs.org/ or `choco install nodejs`
- **Linux**:
  - Ubuntu/Debian: `sudo apt install nodejs npm`
  - Fedora: `sudo dnf install nodejs`
  - Arch: `sudo pacman -S nodejs npm`

## Verification

After installation, verify:

```bash
agent-browser --version
```

You should see output like:
```
agent-browser version 1.x.x
```
