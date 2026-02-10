#!/bin/bash
# Test script to verify agent-browser skill gating

echo "🧪 Testing agent-browser skill gating..."
echo ""

# Check if agent-browser is installed
if command -v agent-browser &> /dev/null; then
    VERSION=$(agent-browser --version 2>&1 | head -1 || echo "unknown")
    echo "✅ agent-browser found: $VERSION"
    echo "   Skill should be marked as AVAILABLE"
else
    echo "❌ agent-browser not found in PATH"
    echo "   Skill should be marked as UNAVAILABLE"
    echo ""
    echo "To install, run:"
    echo "  bash .miniclaw/skills/agent-browser/scripts/install-agent-browser.sh"
fi

echo ""
echo "Checking SkillRegistry status:"
echo "(This would show the actual skill availability in the running application)"
