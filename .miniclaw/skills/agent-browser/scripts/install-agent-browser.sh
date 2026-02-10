#!/bin/bash
# agent-browser installation script for macOS/Linux
# Automatically detects available package managers and installs agent-browser

set -e

echo "🔍 Detecting installation method..."
echo ""

# Check if agent-browser is already installed
if command -v agent-browser &> /dev/null; then
    VERSION=$(agent-browser --version 2>&1 | head -1 || echo "unknown")
    echo "✅ agent-browser is already installed: $VERSION"
    echo ""
    echo "To upgrade, run:"
    echo "  npm update -g @agent-tools/browser"
    exit 0
fi

# Try npm first (most common for Node.js tools)
if command -v npm &> /dev/null; then
    echo "📦 Found npm, installing @agent-tools/browser..."
    echo ""
    npm install -g @agent-tools/browser
    echo ""

    if command -v agent-browser &> /dev/null; then
        VERSION=$(agent-browser --version 2>&1 | head -1 || echo "installed")
        echo "✅ Successfully installed agent-browser: $VERSION"
        echo ""
        echo "You can now use the agent-browser skill!"
        exit 0
    else
        echo "⚠️  Installation completed but agent-browser not found in PATH"
        echo "   Try running: source ~/.bashrc (or ~/.zshrc)"
        exit 1
    fi
fi

# Try Homebrew (macOS)
if command -v brew &> /dev/null; then
    echo "🍺 Found Homebrew, checking for agent-browser formula..."
    echo ""

    # Note: Replace with actual formula name when available
    if brew search agent-browser &> /dev/null; then
        brew install agent-browser
        echo "✅ Successfully installed agent-browser via Homebrew"
        exit 0
    else
        echo "⚠️  agent-browser formula not found in Homebrew"
        echo "   Falling back to npm installation..."
        echo ""
    fi
fi

# No suitable package manager found
echo "❌ No suitable package manager found"
echo ""
echo "Please install one of the following:"
echo ""
echo "1. Node.js with npm:"
echo "   - macOS: brew install node"
echo "   - Ubuntu/Debian: sudo apt install nodejs npm"
echo "   - Other: https://nodejs.org/"
echo ""
echo "2. Then run this script again, or install manually:"
echo "   npm install -g @agent-tools/browser"
echo ""
exit 1
