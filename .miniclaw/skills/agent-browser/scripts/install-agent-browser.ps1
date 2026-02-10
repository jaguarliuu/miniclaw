# agent-browser installation script for Windows
# Automatically detects available package managers and installs agent-browser

$ErrorActionPreference = "Stop"

Write-Host "🔍 Detecting installation method..." -ForegroundColor Cyan
Write-Host ""

# Check if agent-browser is already installed
$agentBrowserPath = Get-Command agent-browser -ErrorAction SilentlyContinue
if ($agentBrowserPath) {
    try {
        $version = & agent-browser --version 2>&1 | Select-Object -First 1
        Write-Host "✅ agent-browser is already installed: $version" -ForegroundColor Green
        Write-Host ""
        Write-Host "To upgrade, run:"
        Write-Host "  npm update -g @agent-tools/browser" -ForegroundColor Yellow
        exit 0
    } catch {
        Write-Host "✅ agent-browser is already installed" -ForegroundColor Green
        exit 0
    }
}

# Try npm first (most common for Node.js tools)
$npmPath = Get-Command npm -ErrorAction SilentlyContinue
if ($npmPath) {
    Write-Host "📦 Found npm, installing @agent-tools/browser..." -ForegroundColor Cyan
    Write-Host ""

    try {
        npm install -g @agent-tools/browser
        Write-Host ""

        # Refresh PATH
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

        $agentBrowserCheck = Get-Command agent-browser -ErrorAction SilentlyContinue
        if ($agentBrowserCheck) {
            try {
                $version = & agent-browser --version 2>&1 | Select-Object -First 1
                Write-Host "✅ Successfully installed agent-browser: $version" -ForegroundColor Green
            } catch {
                Write-Host "✅ Successfully installed agent-browser" -ForegroundColor Green
            }
            Write-Host ""
            Write-Host "You can now use the agent-browser skill!" -ForegroundColor Green
            exit 0
        } else {
            Write-Host "⚠️  Installation completed but agent-browser not found in PATH" -ForegroundColor Yellow
            Write-Host "   Please restart your terminal or run:" -ForegroundColor Yellow
            Write-Host "   refreshenv" -ForegroundColor Yellow
            exit 1
        }
    } catch {
        Write-Host "❌ npm installation failed: $_" -ForegroundColor Red
        exit 1
    }
}

# Try Chocolatey (Windows package manager)
$chocoPath = Get-Command choco -ErrorAction SilentlyContinue
if ($chocoPath) {
    Write-Host "🍫 Found Chocolatey, checking for agent-browser package..." -ForegroundColor Cyan
    Write-Host ""

    try {
        # Note: Replace with actual package name when available
        $chocoSearch = choco search agent-browser --exact --limit-output
        if ($chocoSearch) {
            choco install agent-browser -y
            Write-Host "✅ Successfully installed agent-browser via Chocolatey" -ForegroundColor Green
            exit 0
        } else {
            Write-Host "⚠️  agent-browser package not found in Chocolatey" -ForegroundColor Yellow
            Write-Host "   Please install via npm instead" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "⚠️  Chocolatey check failed, falling back..." -ForegroundColor Yellow
    }
}

# No suitable package manager found
Write-Host "❌ No suitable package manager found" -ForegroundColor Red
Write-Host ""
Write-Host "Please install one of the following:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Node.js with npm:" -ForegroundColor Yellow
Write-Host "   - Download from: https://nodejs.org/" -ForegroundColor White
Write-Host "   - Or use Chocolatey: choco install nodejs" -ForegroundColor White
Write-Host ""
Write-Host "2. Then run this script again, or install manually:" -ForegroundColor Yellow
Write-Host "   npm install -g @agent-tools/browser" -ForegroundColor White
Write-Host ""
Write-Host "3. Or download releases from:" -ForegroundColor Yellow
Write-Host "   https://github.com/hdresearch/nolita/releases" -ForegroundColor White
Write-Host ""
exit 1
