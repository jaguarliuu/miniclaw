# Frontend MCP Server Management Integration Guide

## Overview

This guide describes how to integrate MCP server management into the miniclaw frontend. All backend APIs are ready - the frontend just needs to call the RPC methods and build the UI.

## RPC Methods

### 1. List MCP Servers

**Method**: `mcp.servers.list`

**Request**:
```json
{
  "type": "request",
  "id": "req-123",
  "method": "mcp.servers.list",
  "payload": {}
}
```

**Response**:
```json
{
  "type": "response",
  "id": "req-123",
  "payload": {
    "servers": [
      {
        "id": 1,
        "name": "filesystem",
        "transportType": "STDIO",
        "enabled": true,
        "toolPrefix": "fs_",
        "command": "npx",
        "url": "",
        "createdAt": "2026-02-11T10:00:00",
        "updatedAt": "2026-02-11T10:00:00"
      }
    ]
  }
}
```

### 2. Test Connection

**Method**: `mcp.servers.test`

**Request**:
```json
{
  "type": "request",
  "id": "req-124",
  "method": "mcp.servers.test",
  "payload": {
    "name": "test-server",
    "transportType": "STDIO",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
  }
}
```

**Response**:
```json
{
  "type": "response",
  "id": "req-124",
  "payload": {
    "success": true,
    "message": "Connection successful"
  }
}
```

**Note**: Test connection does NOT save the configuration - it's purely for validation before creating/updating.

### 3. Create MCP Server

**Method**: `mcp.servers.create`

**Request**:
```json
{
  "type": "request",
  "id": "req-125",
  "method": "mcp.servers.create",
  "payload": {
    "name": "my-server",
    "transportType": "STDIO",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
    "workingDir": null,
    "env": [],
    "url": null,
    "enabled": true,
    "toolPrefix": "fs_",
    "requiresHitl": false,
    "hitlTools": [],
    "requestTimeoutSeconds": 30
  }
}
```

**Response**:
```json
{
  "type": "response",
  "id": "req-125",
  "payload": {
    "server": {
      "id": 2,
      "name": "my-server",
      "enabled": true
    }
  }
}
```

**Error Response**:
```json
{
  "type": "response",
  "id": "req-125",
  "error": {
    "code": "MCP_CREATE_FAILED",
    "message": "MCP server with name 'my-server' already exists"
  }
}
```

### 4. Update MCP Server

**Method**: `mcp.servers.update`

**Request**:
```json
{
  "type": "request",
  "id": "req-126",
  "method": "mcp.servers.update",
  "payload": {
    "id": 2,
    "name": "my-server",
    "transportType": "STDIO",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/home"],
    "enabled": true,
    "toolPrefix": "fs_",
    "requiresHitl": false,
    "hitlTools": ["write_file"],
    "requestTimeoutSeconds": 45
  }
}
```

**Response**:
```json
{
  "type": "response",
  "id": "req-126",
  "payload": {
    "server": {
      "id": 2,
      "name": "my-server",
      "enabled": true
    }
  }
}
```

**Note**: Updating will disconnect and reconnect the server if enabled.

### 5. Delete MCP Server

**Method**: `mcp.servers.delete`

**Request**:
```json
{
  "type": "request",
  "id": "req-127",
  "method": "mcp.servers.delete",
  "payload": {
    "id": 2
  }
}
```

**Response**:
```json
{
  "type": "response",
  "id": "req-127",
  "payload": {
    "success": true,
    "message": "MCP server deleted successfully"
  }
}
```

**Note**: Deleting will automatically disconnect the server and remove all its tools from the registry.

## Payload Field Reference

### Required for All Transport Types
- `name`: String, unique identifier
- `transportType`: "STDIO" | "SSE" | "HTTP"
- `enabled`: Boolean, whether to auto-connect on startup
- `toolPrefix`: String, prefix for all tools (e.g., "fs_" → "fs_read_file")

### STDIO Transport Specific
- `command`: String, executable command (e.g., "npx", "python")
- `args`: String[], command arguments
- `workingDir`: String (optional), working directory for the process
- `env`: String[] (optional), environment variables in "KEY=value" format

### SSE/HTTP Transport Specific
- `url`: String, server endpoint URL

### Advanced Settings (Optional)
- `requiresHitl`: Boolean, require confirmation for ALL tools from this server
- `hitlTools`: String[], specific tools that require confirmation (overrides requiresHitl if both set)
- `requestTimeoutSeconds`: Integer, timeout for requests (default: 30)

## UI Component Design

### MCP Server List Page

**Location**: Settings → MCP Servers

**Components**:

1. **Server Table**
   - Columns:
     - Name
     - Type (STDIO/SSE/HTTP badge)
     - Status (Connected/Disconnected indicator with color)
     - Tools Count (number of tools registered)
     - Actions (Edit, Delete, Enable/Disable toggle)
   - Empty State: "No MCP servers configured. Add one to extend MiniClaw's capabilities."

2. **Add Button**
   - Primary button: "+ Add MCP Server"
   - Opens configuration modal

3. **Search/Filter** (Optional for v1)
   - Filter by: Name, Type, Status
   - Sort by: Name, Created Date

4. **Real-time Status**
   - WebSocket updates when server connects/disconnects
   - Tool count updates when tools are registered

### MCP Server Configuration Modal

**Tabs**:

#### Tab 1: Basic Info
- **Name** (required)
  - Input field
  - Validation: Unique, alphanumeric + hyphen/underscore
  - Placeholder: "my-server"

- **Transport Type** (required)
  - Radio buttons or Segmented Control:
    - STDIO (Local Process)
    - SSE (Server-Sent Events)
    - HTTP (REST API)
  - Help text explaining each type

- **Tool Prefix** (optional)
  - Input field
  - Placeholder: "prefix_"
  - Help text: "Prefix for all tools to avoid naming conflicts (e.g., 'fs_' → 'fs_read_file')"

#### Tab 2: Connection Settings

**If STDIO selected**:
- **Command** (required)
  - Input field
  - Placeholder: "npx"
  - Common examples dropdown: npx, python, node, /usr/bin/my-server

- **Arguments** (required for most servers)
  - Array input (add/remove fields)
  - Example for filesystem: `["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]`

- **Working Directory** (optional)
  - Path input with file picker
  - Placeholder: "/path/to/working/dir"

- **Environment Variables** (optional)
  - Key-value pair inputs
  - Format: KEY=value
  - Example: DATABASE_URL=postgresql://localhost/db

**If SSE or HTTP selected**:
- **Server URL** (required)
  - URL input with validation
  - Placeholder: "http://localhost:3000/sse" or "http://localhost:8080/mcp"

#### Tab 3: Advanced

- **Request Timeout**
  - Number input
  - Default: 30 seconds
  - Range: 5-300 seconds

- **Human-in-the-Loop (HITL) Settings**
  - Checkbox: "Require confirmation for all tools"
  - OR
  - Multi-select: "Require confirmation for specific tools"
    - Shows list of tools after connection test (if available)
    - Example: write_file, delete_file, execute_command

- **Enable on Creation**
  - Toggle: Enabled / Disabled
  - Help text: "If enabled, server will connect immediately after creation"

### Modal Actions

**Bottom Bar**:
- **Test Connection** button (left side)
  - Secondary button
  - Shows loading spinner during test
  - On success: Green checkmark + "Connection successful"
  - On failure: Red X + error message
  - Disabled if required fields are empty

- **Cancel** button (right side)
  - Closes modal without saving

- **Save** button (right side)
  - Primary button
  - Enabled only after successful connection test
  - Creates or updates server

## User Flow

### Creating a New MCP Server

```
1. User navigates to Settings → MCP Servers
2. User clicks "+ Add MCP Server"
3. Modal opens with "Basic Info" tab active
4. User enters:
   - Name: "my-filesystem"
   - Transport Type: STDIO (selected)
   - Tool Prefix: "fs_"
5. User switches to "Connection Settings" tab
6. User enters:
   - Command: "npx"
   - Arguments: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
7. User clicks "Test Connection"
   → Frontend sends mcp.servers.test RPC
   → Backend spawns process, tests connection, returns result
   → Frontend shows: ✓ "Connection successful"
8. User clicks "Save"
   → Frontend sends mcp.servers.create RPC
   → Backend saves to database and connects server
   → Backend discovers and registers tools
   → Frontend receives success response
   → Modal closes
9. Server list refreshes, new server appears with "Connected" status
10. Tool count updates (e.g., "5 tools")
11. MCP tools are immediately available in chat (e.g., /fs_read_file)
```

### Editing an Existing MCP Server

```
1. User clicks "Edit" on a server row
2. Modal opens with existing configuration pre-filled
3. User modifies configuration (e.g., changes args from "/tmp" to "/home")
4. User clicks "Test Connection" to validate changes
5. User clicks "Save"
   → Frontend sends mcp.servers.update RPC
   → Backend disconnects old connection
   → Backend updates database
   → Backend connects with new configuration
   → Frontend receives success response
6. Server list refreshes with updated info
```

### Deleting an MCP Server

```
1. User clicks "Delete" on a server row
2. Confirmation dialog appears:
   "Are you sure you want to delete 'my-filesystem'?
    All tools from this server will be removed."
3. User confirms
   → Frontend sends mcp.servers.delete RPC
   → Backend disconnects server
   → Backend removes all tools from registry
   → Backend deletes from database
4. Server disappears from list
5. Tools are no longer available
```

## Example Vue 3 Component

```vue
<template>
  <div class="mcp-servers-page">
    <div class="header">
      <h2>MCP Servers</h2>
      <button @click="openModal('create')" class="btn-primary">
        + Add MCP Server
      </button>
    </div>

    <table class="servers-table" v-if="servers.length > 0">
      <thead>
        <tr>
          <th>Name</th>
          <th>Type</th>
          <th>Status</th>
          <th>Tools</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="server in servers" :key="server.id">
          <td>{{ server.name }}</td>
          <td>
            <span class="badge" :class="`badge-${server.transportType.toLowerCase()}`">
              {{ server.transportType }}
            </span>
          </td>
          <td>
            <span class="status" :class="{ connected: server.enabled }">
              {{ server.enabled ? 'Connected' : 'Disconnected' }}
            </span>
          </td>
          <td>{{ server.toolCount || 0 }} tools</td>
          <td>
            <button @click="openModal('edit', server)" class="btn-icon">Edit</button>
            <button @click="deleteServer(server)" class="btn-icon">Delete</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else class="empty-state">
      <p>No MCP servers configured.</p>
      <p>Add one to extend MiniClaw's capabilities.</p>
    </div>

    <!-- Modal Component -->
    <McpServerModal
      v-if="showModal"
      :mode="modalMode"
      :server="selectedServer"
      @close="closeModal"
      @success="handleSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRpc } from '@/composables/useRpc'
import McpServerModal from './McpServerModal.vue'

const { call } = useRpc()

const servers = ref([])
const showModal = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const selectedServer = ref(null)

const loadServers = async () => {
  const response = await call('mcp.servers.list', {})
  if (response.payload) {
    servers.value = response.payload.servers
  }
}

const openModal = (mode: 'create' | 'edit', server = null) => {
  modalMode.value = mode
  selectedServer.value = server
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  selectedServer.value = null
}

const handleSuccess = () => {
  loadServers()
  closeModal()
}

const deleteServer = async (server) => {
  if (!confirm(`Are you sure you want to delete '${server.name}'?`)) {
    return
  }

  const response = await call('mcp.servers.delete', { id: server.id })
  if (response.payload?.success) {
    loadServers()
  }
}

onMounted(() => {
  loadServers()
})
</script>
```

### Modal Component Example

```vue
<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-content">
      <h3>{{ mode === 'create' ? 'Add' : 'Edit' }} MCP Server</h3>

      <div class="tabs">
        <button
          v-for="tab in tabs"
          :key="tab"
          :class="{ active: activeTab === tab }"
          @click="activeTab = tab"
        >
          {{ tab }}
        </button>
      </div>

      <!-- Tab 1: Basic Info -->
      <div v-show="activeTab === 'Basic Info'" class="tab-content">
        <div class="form-group">
          <label>Name *</label>
          <input v-model="config.name" placeholder="my-server" />
        </div>

        <div class="form-group">
          <label>Transport Type *</label>
          <select v-model="config.transportType">
            <option value="STDIO">STDIO (Local Process)</option>
            <option value="SSE">SSE (Server-Sent Events)</option>
            <option value="HTTP">HTTP (REST API)</option>
          </select>
        </div>

        <div class="form-group">
          <label>Tool Prefix</label>
          <input v-model="config.toolPrefix" placeholder="fs_" />
          <p class="help-text">Prefix for all tools to avoid naming conflicts</p>
        </div>
      </div>

      <!-- Tab 2: Connection Settings -->
      <div v-show="activeTab === 'Connection'" class="tab-content">
        <div v-if="config.transportType === 'STDIO'">
          <div class="form-group">
            <label>Command *</label>
            <input v-model="config.command" placeholder="npx" />
          </div>

          <div class="form-group">
            <label>Arguments</label>
            <div v-for="(arg, index) in config.args" :key="index" class="array-input">
              <input v-model="config.args[index]" />
              <button @click="config.args.splice(index, 1)">Remove</button>
            </div>
            <button @click="config.args.push('')">+ Add Argument</button>
          </div>
        </div>

        <div v-else>
          <div class="form-group">
            <label>Server URL *</label>
            <input v-model="config.url" placeholder="http://localhost:3000/sse" />
          </div>
        </div>
      </div>

      <!-- Tab 3: Advanced -->
      <div v-show="activeTab === 'Advanced'" class="tab-content">
        <div class="form-group">
          <label>Request Timeout (seconds)</label>
          <input v-model.number="config.requestTimeoutSeconds" type="number" min="5" max="300" />
        </div>

        <div class="form-group">
          <label>
            <input v-model="config.requiresHitl" type="checkbox" />
            Require confirmation for all tools
          </label>
        </div>

        <div class="form-group">
          <label>
            <input v-model="config.enabled" type="checkbox" />
            Enable on creation
          </label>
        </div>
      </div>

      <!-- Actions -->
      <div class="modal-actions">
        <button @click="testConnection" :disabled="!canTest" class="btn-secondary">
          {{ testing ? 'Testing...' : 'Test Connection' }}
        </button>

        <div v-if="testResult" class="test-result" :class="{ success: testResult.success }">
          {{ testResult.message }}
        </div>

        <div class="right-actions">
          <button @click="$emit('close')" class="btn-secondary">Cancel</button>
          <button @click="save" :disabled="!canSave" class="btn-primary">Save</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRpc } from '@/composables/useRpc'

const props = defineProps<{
  mode: 'create' | 'edit'
  server: any
}>()

const emit = defineEmits(['close', 'success'])

const { call } = useRpc()

const tabs = ['Basic Info', 'Connection', 'Advanced']
const activeTab = ref('Basic Info')

const config = ref({
  name: props.server?.name || '',
  transportType: props.server?.transportType || 'STDIO',
  command: props.server?.command || '',
  args: props.server?.args || [],
  url: props.server?.url || '',
  enabled: props.server?.enabled !== false,
  toolPrefix: props.server?.toolPrefix || '',
  requiresHitl: props.server?.requiresHitl || false,
  requestTimeoutSeconds: props.server?.requestTimeoutSeconds || 30
})

const testing = ref(false)
const testResult = ref(null)

const canTest = computed(() => {
  if (!config.value.name) return false
  if (config.value.transportType === 'STDIO') {
    return !!config.value.command
  }
  return !!config.value.url
})

const canSave = computed(() => {
  return testResult.value?.success === true
})

const testConnection = async () => {
  testing.value = true
  testResult.value = null

  try {
    const response = await call('mcp.servers.test', config.value)
    testResult.value = response.payload
  } catch (error) {
    testResult.value = { success: false, message: error.message }
  } finally {
    testing.value = false
  }
}

const save = async () => {
  const method = props.mode === 'create' ? 'mcp.servers.create' : 'mcp.servers.update'
  const payload = props.mode === 'edit' ? { ...config.value, id: props.server.id } : config.value

  const response = await call(method, payload)
  if (response.payload) {
    emit('success')
  }
}

// Reset test result when config changes
watch(config, () => {
  testResult.value = null
}, { deep: true })
</script>
```

## Backend Integration Points

**All backend work is complete!** The frontend only needs to:

1. **Call RPC methods** via WebSocket connection
2. **Handle responses** (success/error)
3. **Update UI** based on response data

**Real-time updates**:
- Tool registration happens automatically when servers connect
- Health checks run every 60 seconds by default
- Servers auto-reconnect on failure

**No additional backend work needed** - just build the UI and call the RPC methods.

## Testing Checklist

### Functional Testing
- [ ] List servers (empty state and with data)
- [ ] Test connection (STDIO, SSE, HTTP)
- [ ] Create server (all transport types)
- [ ] Update server configuration
- [ ] Delete server
- [ ] Enable/disable server toggle
- [ ] Tool prefix validation
- [ ] Unique name validation

### Error Handling
- [ ] Invalid configuration (missing required fields)
- [ ] Connection test failure
- [ ] Duplicate server name
- [ ] Server not found (edit/delete)
- [ ] Network errors
- [ ] Timeout handling

### Integration Testing
- [ ] Tools appear in tool list after server creation
- [ ] Tools disappear after server deletion
- [ ] Status updates in real-time
- [ ] Tool count updates correctly
- [ ] HITL configuration works for MCP tools

### UX Testing
- [ ] Form validation provides helpful messages
- [ ] Loading states are clear
- [ ] Success/error feedback is visible
- [ ] Modal closes properly
- [ ] Tab navigation works
- [ ] Responsive design (mobile/tablet)

## Quick Start for Frontend Developers

1. **Clone and setup**:
   ```bash
   cd miniclaw-ui
   npm install
   npm run dev
   ```

2. **Add MCP Servers page**:
   - Create `src/views/settings/McpServers.vue`
   - Add route to router
   - Link from Settings menu

3. **Test with local MCP server**:
   ```bash
   # Install official filesystem server
   npx -y @modelcontextprotocol/server-filesystem /tmp

   # In UI:
   - Name: filesystem
   - Type: STDIO
   - Command: npx
   - Args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
   - Test → Should succeed
   - Save → Server connects, tools appear
   ```

4. **Verify tools are available**:
   - Check tool list (`tool.list` RPC)
   - Should see: `fs_read_file`, `fs_write_file`, etc.
   - Try calling a tool from chat

## References

- [MCP Integration Guide](./mcp-integration.md) - Full backend implementation details
- [MCP Specification](https://spec.modelcontextprotocol.io/) - Official MCP protocol spec
- [Example Configuration](./examples/mcp-config-example.yml) - Example server configurations
