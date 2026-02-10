---
name: agent-browser
description: Automates browser interactions for web testing, form filling, screenshots, and data extraction. Use when the user needs to navigate websites, interact with web pages, fill forms, take screenshots, test web applications, or extract information from web pages.
metadata:
  miniclaw:
    requires:
      anyBins: ["agent-browser", "agent-browser.cmd"]
---

# Browser Automation with agent-browser

## Installation Required

This skill requires `agent-browser` to be installed. If you see an error that this skill is unavailable, follow the installation guide below.

### Quick Install

Choose your preferred method:

**Option 1: npm (Recommended)**
```bash
npm install -g @agent-tools/browser
```

**Option 2: Use the installation script**
```bash
# macOS/Linux
bash scripts/install-agent-browser.sh

# Windows
powershell scripts/install-agent-browser.ps1
```

**Option 3: Manual installation**
- Download from: https://github.com/hdresearch/nolita/releases
- Add to your system PATH

**Verify installation:**
```bash
agent-browser --version
```

---

## Platform-Specific Notes

**Windows Users:**
- Use `agent-browser.cmd` instead of `agent-browser` in commands
- Or add `.cmd` extension: `agent-browser.cmd open <url>`
- The installation script handles this automatically

**All Platforms:**
- After installation, you may need to restart your terminal
- Verify with: `agent-browser --version`

---

## Quick start

```bash
agent-browser open <url>        # Navigate to page
agent-browser snapshot -i       # Get interactive elements with refs
agent-browser click @e1         # Click element by ref
agent-browser fill @e2 "text"   # Fill input by ref
agent-browser close             # Close browser
```

**Windows:** Replace `agent-browser` with `agent-browser.cmd` in all commands above.

## Core workflow

1. Navigate: `.\agent-browser.cmd open <url>`
2. Snapshot: `.\agent-browser.cmd snapshot -i` (returns elements with refs like `@e1`, `@e2`)
3. Interact using refs from the snapshot
4. Re-snapshot after navigation or significant DOM changes

## Commands

### Navigation
```bash
.\agent-browser.cmd open <url>      # Navigate to URL
.\agent-browser.cmd back            # Go back
.\agent-browser.cmd forward         # Go forward
.\agent-browser.cmd reload          # Reload page
.\agent-browser.cmd close           # Close browser
```

### Snapshot (page analysis)
```bash
.\agent-browser.cmd snapshot            # Full accessibility tree
.\agent-browser.cmd snapshot -i         # Interactive elements only (recommended)
.\agent-browser.cmd snapshot -c         # Compact output
.\agent-browser.cmd snapshot -d 3       # Limit depth to 3
.\agent-browser.cmd snapshot -s "#main" # Scope to CSS selector
```

### Interactions (use @refs from snapshot)
```bash
.\agent-browser.cmd click @e1           # Click
.\agent-browser.cmd dblclick @e1        # Double-click
.\agent-browser.cmd focus @e1           # Focus element
.\agent-browser.cmd fill @e2 "text"     # Clear and type
.\agent-browser.cmd type @e2 "text"     # Type without clearing
.\agent-browser.cmd press Enter         # Press key
.\agent-browser.cmd press Control+a     # Key combination
.\agent-browser.cmd keydown Shift       # Hold key down
.\agent-browser.cmd keyup Shift         # Release key
.\agent-browser.cmd hover @e1           # Hover
.\agent-browser.cmd check @e1           # Check checkbox
.\agent-browser.cmd uncheck @e1         # Uncheck checkbox
.\agent-browser.cmd select @e1 "value"  # Select dropdown
.\agent-browser.cmd scroll down 500     # Scroll page
.\agent-browser.cmd scrollintoview @e1  # Scroll element into view
.\agent-browser.cmd drag @e1 @e2        # Drag and drop
.\agent-browser.cmd upload @e1 file.pdf # Upload files
```

### Get information
```bash
.\agent-browser.cmd get text @e1        # Get element text
.\agent-browser.cmd get html @e1        # Get innerHTML
.\agent-browser.cmd get value @e1       # Get input value
.\agent-browser.cmd get attr @e1 href   # Get attribute
.\agent-browser.cmd get title           # Get page title
.\agent-browser.cmd get url             # Get current URL
.\agent-browser.cmd get count ".item"   # Count matching elements
.\agent-browser.cmd get box @e1         # Get bounding box
```

### Check state
```bash
.\agent-browser.cmd is visible @e1      # Check if visible
.\agent-browser.cmd is enabled @e1      # Check if enabled
.\agent-browser.cmd is checked @e1      # Check if checked
```

### Screenshots & PDF
```bash
.\agent-browser.cmd screenshot          # Screenshot to stdout
.\agent-browser.cmd screenshot path.png # Save to file
.\agent-browser.cmd screenshot --full   # Full page
.\agent-browser.cmd pdf output.pdf      # Save as PDF
```

### Video recording
```bash
.\agent-browser.cmd record start ./demo.webm    # Start recording (uses current URL + state)
.\agent-browser.cmd click @e1                   # Perform actions
.\agent-browser.cmd record stop                 # Stop and save video
.\agent-browser.cmd record restart ./take2.webm # Stop current + start new recording
```
Recording creates a fresh context but preserves cookies/storage from your session. If no URL is provided, it automatically returns to your current page. For smooth demos, explore first, then start recording.

### Wait
```bash
.\agent-browser.cmd wait @e1                     # Wait for element
.\agent-browser.cmd wait 2000                    # Wait milliseconds
.\agent-browser.cmd wait --text "Success"        # Wait for text
.\agent-browser.cmd wait --url "**/dashboard"    # Wait for URL pattern
.\agent-browser.cmd wait --load networkidle      # Wait for network idle
.\agent-browser.cmd wait --fn "window.ready"     # Wait for JS condition
```

### Mouse control
```bash
.\agent-browser.cmd mouse move 100 200      # Move mouse
.\agent-browser.cmd mouse down left         # Press button
.\agent-browser.cmd mouse up left           # Release button
.\agent-browser.cmd mouse wheel 100         # Scroll wheel
```

### Semantic locators (alternative to refs)
```bash
.\agent-browser.cmd find role button click --name "Submit"
.\agent-browser.cmd find text "Sign In" click
.\agent-browser.cmd find label "Email" fill "user@test.com"
.\agent-browser.cmd find first ".item" click
.\agent-browser.cmd find nth 2 "a" text
```

### Browser settings
```bash
.\agent-browser.cmd set viewport 1920 1080      # Set viewport size
.\agent-browser.cmd set device "iPhone 14"      # Emulate device
.\agent-browser.cmd set geo 37.7749 -122.4194   # Set geolocation
.\agent-browser.cmd set offline on              # Toggle offline mode
.\agent-browser.cmd set headers '{"X-Key":"v"}' # Extra HTTP headers
.\agent-browser.cmd set credentials user pass   # HTTP basic auth
.\agent-browser.cmd set media dark              # Emulate color scheme
```

### Cookies & Storage
```bash
.\agent-browser.cmd cookies                     # Get all cookies
.\agent-browser.cmd cookies set name value      # Set cookie
.\agent-browser.cmd cookies clear               # Clear cookies
.\agent-browser.cmd storage local               # Get all localStorage
.\agent-browser.cmd storage local key           # Get specific key
.\agent-browser.cmd storage local set k v       # Set value
.\agent-browser.cmd storage local clear         # Clear all
```

### Network
```bash
.\agent-browser.cmd network route <url>              # Intercept requests
.\agent-browser.cmd network route <url> --abort      # Block requests
.\agent-browser.cmd network route <url> --body '{}'  # Mock response
.\agent-browser.cmd network unroute [url]            # Remove routes
.\agent-browser.cmd network requests                 # View tracked requests
.\agent-browser.cmd network requests --filter api    # Filter requests
```

### Tabs & Windows
```bash
.\agent-browser.cmd tab                 # List tabs
.\agent-browser.cmd tab new [url]       # New tab
.\agent-browser.cmd tab 2               # Switch to tab
.\agent-browser.cmd tab close           # Close tab
.\agent-browser.cmd window new          # New window
```

### Frames
```bash
.\agent-browser.cmd frame "#iframe"     # Switch to iframe
.\agent-browser.cmd frame main          # Back to main frame
```

### Dialogs
```bash
.\agent-browser.cmd dialog accept [text]  # Accept dialog
.\agent-browser.cmd dialog dismiss        # Dismiss dialog
```

### JavaScript
```bash
.\agent-browser.cmd eval "document.title"   # Run JavaScript
```

## Example: Form submission

```bash
.\agent-browser.cmd open https://example.com/form
.\agent-browser.cmd snapshot -i
# Output shows: textbox "Email" [ref=e1], textbox "Password" [ref=e2], button "Submit" [ref=e3]

.\agent-browser.cmd fill @e1 "user@example.com"
.\agent-browser.cmd fill @e2 "password123"
.\agent-browser.cmd click @e3
.\agent-browser.cmd wait --load networkidle
.\agent-browser.cmd snapshot -i  # Check result
```

## Example: Authentication with saved state

```bash
# Login once
.\agent-browser.cmd open https://app.example.com/login
.\agent-browser.cmd snapshot -i
.\agent-browser.cmd fill @e1 "username"
.\agent-browser.cmd fill @e2 "password"
.\agent-browser.cmd click @e3
.\agent-browser.cmd wait --url "**/dashboard"
.\agent-browser.cmd state save auth.json

# Later sessions: load saved state
.\agent-browser.cmd state load auth.json
.\agent-browser.cmd open https://app.example.com/dashboard
```

## Sessions (parallel browsers)

```bash
.\agent-browser.cmd --session test1 open site-a.com
.\agent-browser.cmd --session test2 open site-b.com
.\agent-browser.cmd session list
```

## JSON output (for parsing)

Add `--json` for machine-readable output:
```bash
.\agent-browser.cmd snapshot -i --json
.\agent-browser.cmd get text @e1 --json
```

## Debugging

```bash
.\agent-browser.cmd open example.com --headed              # Show browser window
.\agent-browser.cmd console                                # View console messages
.\agent-browser.cmd errors                                 # View page errors
.\agent-browser.cmd record start ./debug.webm   # Record from current page
.\agent-browser.cmd record stop                            # Save recording
.\agent-browser.cmd open example.com --headed  # Show browser window
.\agent-browser.cmd --cdp 9222 snapshot        # Connect via CDP
.\agent-browser.cmd console                    # View console messages
.\agent-browser.cmd console --clear            # Clear console
.\agent-browser.cmd errors                     # View page errors
.\agent-browser.cmd errors --clear             # Clear errors
.\agent-browser.cmd highlight @e1              # Highlight element
.\agent-browser.cmd trace start                # Start recording trace
.\agent-browser.cmd trace stop trace.zip       # Stop and save trace
```
