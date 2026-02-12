<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useDataSource } from '@/composables/useDataSource'
import Select, { type SelectOption } from '@/components/common/Select.vue'
import type {
  DataSourceType,
  DataSourceInfo,
  JdbcConnectionConfig,
  FileConnectionConfig,
  SecurityConfig
} from '@/types'

const {
  dataSources,
  loading,
  error,
  loadDataSources,
  createDataSource,
  deleteDataSource,
  testConnection,
  enableDataSource,
  disableDataSource
} = useDataSource()

// Form state
const showForm = ref(false)
const formName = ref('')
const formType = ref<DataSourceType>('MYSQL')
const formError = ref<string | null>(null)
const submitting = ref(false)

// JDBC fields
const formHost = ref('')
const formPort = ref<number>(3306)
const formDatabase = ref('')
const formUsername = ref('')
const formPassword = ref('')
const passwordVisible = ref(false)

// File fields
const formFilePath = ref('')
const formEncoding = ref('UTF-8')
const formDelimiter = ref(',')
const formHasHeader = ref(true)

// Security config
const formMaxConnections = ref(10)
const formQueryTimeout = ref(30)
const formMaxResultRows = ref(1000)
const formReadOnly = ref(true)

// Testing state per datasource
const testingDataSources = ref<Set<string>>(new Set())

// Delete confirmation
const confirmDeleteId = ref<string | null>(null)

// Select options
const dataSourceTypeOptions: SelectOption<DataSourceType>[] = [
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'PostgreSQL', value: 'POSTGRESQL' },
  { label: 'Oracle', value: 'ORACLE' },
  { label: 'GaussDB', value: 'GAUSS' },
  { label: 'CSV File', value: 'CSV' },
  { label: 'Excel (XLSX)', value: 'XLSX' }
]

const isJdbcType = computed(() =>
  ['MYSQL', 'POSTGRESQL', 'ORACLE', 'GAUSS'].includes(formType.value)
)

const isFileType = computed(() =>
  ['CSV', 'XLSX'].includes(formType.value)
)

function resetForm() {
  formName.value = ''
  formType.value = 'MYSQL'
  formHost.value = ''
  formPort.value = 3306
  formDatabase.value = ''
  formUsername.value = ''
  formPassword.value = ''
  passwordVisible.value = false
  formFilePath.value = ''
  formEncoding.value = 'UTF-8'
  formDelimiter.value = ','
  formHasHeader.value = true
  formMaxConnections.value = 10
  formQueryTimeout.value = 30
  formMaxResultRows.value = 1000
  formReadOnly.value = true
  formError.value = null
}

function openForm() {
  resetForm()
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  resetForm()
}

function updateDefaultPort() {
  switch (formType.value) {
    case 'MYSQL':
      formPort.value = 3306
      break
    case 'POSTGRESQL':
      formPort.value = 5432
      break
    case 'ORACLE':
      formPort.value = 1521
      break
    case 'GAUSS':
      formPort.value = 5432
      break
  }
}

async function handleSubmit() {
  if (!formName.value.trim()) {
    formError.value = 'Name is required'
    return
  }

  let connectionConfig: JdbcConnectionConfig | FileConnectionConfig

  if (isJdbcType.value) {
    if (!formHost.value || !formDatabase.value || !formUsername.value || !formPassword.value) {
      formError.value = 'Please fill in all required JDBC fields'
      return
    }
    connectionConfig = {
      type: 'jdbc',
      host: formHost.value,
      port: formPort.value,
      database: formDatabase.value,
      username: formUsername.value,
      password: formPassword.value
    }
  } else {
    if (!formFilePath.value) {
      formError.value = 'File path is required'
      return
    }
    connectionConfig = {
      type: 'file',
      filePath: formFilePath.value,
      encoding: formEncoding.value,
      delimiter: formDelimiter.value,
      hasHeader: formHasHeader.value
    }
  }

  const securityConfig: SecurityConfig = {
    maxConnections: formMaxConnections.value,
    queryTimeout: formQueryTimeout.value,
    maxResultRows: formMaxResultRows.value,
    readOnly: formReadOnly.value
  }

  submitting.value = true
  formError.value = null
  try {
    await createDataSource({
      name: formName.value.trim(),
      type: formType.value,
      connectionConfig,
      securityConfig
    })
    closeForm()
  } catch (e) {
    formError.value = e instanceof Error ? e.message : 'Failed to create data source'
  } finally {
    submitting.value = false
  }
}

async function handleTest(dsId: string) {
  testingDataSources.value.add(dsId)
  try {
    await testConnection(dsId)
  } catch {
    // Error handled in composable
  } finally {
    testingDataSources.value.delete(dsId)
  }
}

async function handleToggleStatus(ds: DataSourceInfo) {
  try {
    if (ds.status === 'DISABLED') {
      await enableDataSource(ds.id)
    } else {
      await disableDataSource(ds.id)
    }
  } catch {
    // Error handled in composable
  }
}

async function handleDelete(dsId: string) {
  try {
    await deleteDataSource(dsId)
  } catch {
    // Error handled in composable
  } finally {
    confirmDeleteId.value = null
  }
}

function getTypeBadgeClass(type: DataSourceType): string {
  switch (type) {
    case 'MYSQL': return 'badge-mysql'
    case 'POSTGRESQL': return 'badge-postgresql'
    case 'ORACLE': return 'badge-oracle'
    case 'GAUSS': return 'badge-gauss'
    case 'CSV': return 'badge-csv'
    case 'XLSX': return 'badge-xlsx'
    default: return ''
  }
}

function getStatusClass(ds: DataSourceInfo): string {
  switch (ds.status) {
    case 'ACTIVE': return 'status-active'
    case 'INACTIVE': return 'status-inactive'
    case 'ERROR': return 'status-error'
    case 'DISABLED': return 'status-disabled'
    default: return 'status-unknown'
  }
}

onMounted(() => {
  loadDataSources()
})
</script>

<template>
  <div class="datasources-section">
    <header class="section-header">
      <div class="header-top">
        <div>
          <h2 class="section-title">/datasources</h2>
          <p class="section-subtitle">Database and file data source management</p>
        </div>
        <button class="add-btn" @click="openForm">+ Add Data Source</button>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && dataSources.length === 0" class="loading-state">
      Loading data sources...
    </div>

    <!-- Error -->
    <div v-if="error && dataSources.length === 0" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadDataSources">Retry</button>
    </div>

    <!-- Add Form -->
    <div v-if="showForm" class="form-panel">
      <h3 class="form-title">Create Data Source</h3>

      <div class="form-grid">
        <div class="form-group">
          <label class="form-label">Name *</label>
          <input v-model="formName" class="form-input" placeholder="e.g. Production MySQL" />
        </div>

        <div class="form-group">
          <label class="form-label">Type *</label>
          <Select
            v-model="formType"
            :options="dataSourceTypeOptions"
            @update:modelValue="updateDefaultPort"
          />
        </div>

        <!-- JDBC Fields -->
        <template v-if="isJdbcType">
          <div class="form-group">
            <label class="form-label">Host *</label>
            <input v-model="formHost" class="form-input" placeholder="e.g. localhost" />
          </div>

          <div class="form-group">
            <label class="form-label">Port *</label>
            <input v-model.number="formPort" type="number" class="form-input" />
          </div>

          <div class="form-group">
            <label class="form-label">Database *</label>
            <input v-model="formDatabase" class="form-input" placeholder="Database name" />
          </div>

          <div class="form-group">
            <label class="form-label">Username *</label>
            <input v-model="formUsername" class="form-input" placeholder="Database user" />
          </div>

          <div class="form-group credential-group">
            <div class="credential-label-row">
              <label class="form-label">Password *</label>
              <button
                type="button"
                class="visibility-toggle"
                @click="passwordVisible = !passwordVisible"
              >
                {{ passwordVisible ? 'Hide' : 'Show' }}
              </button>
            </div>
            <input
              v-model="formPassword"
              :type="passwordVisible ? 'text' : 'password'"
              class="form-input"
              placeholder="Database password"
            />
          </div>
        </template>

        <!-- File Fields -->
        <template v-if="isFileType">
          <div class="form-group">
            <label class="form-label">File Path *</label>
            <input v-model="formFilePath" class="form-input" placeholder="e.g. data/sales.csv" />
          </div>

          <div class="form-group" v-if="formType === 'CSV'">
            <label class="form-label">Encoding</label>
            <input v-model="formEncoding" class="form-input" placeholder="UTF-8" />
          </div>

          <div class="form-group" v-if="formType === 'CSV'">
            <label class="form-label">Delimiter</label>
            <input v-model="formDelimiter" class="form-input" placeholder="," maxlength="1" />
          </div>

          <div class="form-group checkbox-group">
            <label class="checkbox-label">
              <input type="checkbox" v-model="formHasHeader" />
              <span>File has header row</span>
            </label>
          </div>
        </template>

        <!-- Security Config -->
        <div class="form-group">
          <label class="form-label">Max Connections</label>
          <input v-model.number="formMaxConnections" type="number" class="form-input" />
        </div>

        <div class="form-group">
          <label class="form-label">Query Timeout (s)</label>
          <input v-model.number="formQueryTimeout" type="number" class="form-input" />
        </div>

        <div class="form-group">
          <label class="form-label">Max Result Rows</label>
          <input v-model.number="formMaxResultRows" type="number" class="form-input" />
        </div>

        <div class="form-group checkbox-group">
          <label class="checkbox-label">
            <input type="checkbox" v-model="formReadOnly" />
            <span>Read-only (recommended)</span>
          </label>
        </div>
      </div>

      <div v-if="formError" class="form-error">{{ formError }}</div>

      <div class="form-actions">
        <button class="cancel-btn" @click="closeForm" :disabled="submitting">Cancel</button>
        <button class="submit-btn" @click="handleSubmit" :disabled="submitting">
          {{ submitting ? 'Creating...' : 'Create Data Source' }}
        </button>
      </div>
    </div>

    <!-- Data Sources List -->
    <div v-if="!loading || dataSources.length > 0" class="datasources-list">
      <div v-for="ds in dataSources" :key="ds.id" class="datasource-card">
        <div class="card-header">
          <div class="header-left">
            <h3 class="datasource-name">{{ ds.name }}</h3>
            <span class="datasource-type" :class="getTypeBadgeClass(ds.type)">{{ ds.type }}</span>
            <span class="datasource-status" :class="getStatusClass(ds)">{{ ds.status }}</span>
          </div>
          <div class="header-actions">
            <button
              class="action-btn test-btn"
              @click="handleTest(ds.id)"
              :disabled="testingDataSources.has(ds.id)"
              title="Test connection"
            >
              {{ testingDataSources.has(ds.id) ? 'Testing...' : 'Test' }}
            </button>
            <button
              class="action-btn toggle-btn"
              @click="handleToggleStatus(ds)"
              :title="ds.status === 'DISABLED' ? 'Enable' : 'Disable'"
            >
              {{ ds.status === 'DISABLED' ? 'Enable' : 'Disable' }}
            </button>
            <button
              class="action-btn delete-btn"
              @click="confirmDeleteId = ds.id"
              title="Delete"
            >
              Delete
            </button>
          </div>
        </div>

        <div class="card-content">
          <div class="datasource-info">
            <div class="info-item" v-if="ds.connectionConfig.type === 'jdbc'">
              <span class="info-label">Host:</span>
              <span class="info-value">{{ (ds.connectionConfig as any).host }}:{{ (ds.connectionConfig as any).port }}</span>
            </div>
            <div class="info-item" v-if="ds.connectionConfig.type === 'jdbc'">
              <span class="info-label">Database:</span>
              <span class="info-value">{{ (ds.connectionConfig as any).database }}</span>
            </div>
            <div class="info-item" v-if="ds.connectionConfig.type === 'file'">
              <span class="info-label">File Path:</span>
              <span class="info-value">{{ (ds.connectionConfig as any).filePath }}</span>
            </div>
            <div class="info-item" v-if="ds.lastTestedAt">
              <span class="info-label">Last Tested:</span>
              <span class="info-value">{{ new Date(ds.lastTestedAt).toLocaleString() }}</span>
            </div>
            <div class="info-item error-info" v-if="ds.lastError">
              <span class="info-label">Last Error:</span>
              <span class="info-value error-text">{{ ds.lastError }}</span>
            </div>
          </div>
        </div>

        <!-- Delete Confirmation -->
        <div v-if="confirmDeleteId === ds.id" class="delete-confirm">
          <p class="confirm-message">Are you sure you want to delete "<strong>{{ ds.name }}</strong>"?</p>
          <div class="confirm-actions">
            <button class="cancel-btn" @click="confirmDeleteId = null">Cancel</button>
            <button class="delete-confirm-btn" @click="handleDelete(ds.id)">Delete</button>
          </div>
        </div>
      </div>

      <div v-if="dataSources.length === 0 && !loading" class="empty-state">
        <p>No data sources configured yet.</p>
        <button class="add-btn-secondary" @click="openForm">Create your first data source</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.datasources-section {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.section-header {
  margin-bottom: 24px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: var(--color-black);
}

.section-subtitle {
  font-size: 14px;
  color: var(--color-gray-500);
  margin: 0;
}

.add-btn, .add-btn-secondary {
  padding: 8px 16px;
  background: var(--color-black);
  color: var(--color-white);
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: background var(--duration-fast);
}

.add-btn:hover, .add-btn-secondary:hover {
  background: var(--color-gray-800);
}

.loading-state, .error-state {
  padding: 40px;
  text-align: center;
  color: var(--color-gray-500);
}

.retry-btn {
  margin-top: 16px;
  padding: 8px 16px;
  background: var(--color-gray-50);
  border: var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.form-panel {
  background: var(--color-gray-50);
  border: var(--border);
  border-radius: var(--radius-lg);
  padding: 24px;
  margin-bottom: 24px;
}

.form-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 20px 0;
  color: var(--color-black);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-gray-600);
}

.form-input {
  padding: 8px 12px;
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--color-black);
}

.credential-group {
  grid-column: 1 / -1;
}

.credential-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.visibility-toggle {
  padding: 4px 8px;
  background: transparent;
  border: none;
  color: var(--color-gray-600);
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
}

.checkbox-group {
  padding-top: 8px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--color-black);
  cursor: pointer;
}

.form-error {
  padding: 12px;
  background: var(--color-red-50);
  border: 1px solid var(--color-red-200);
  border-radius: var(--radius-md);
  color: var(--color-red-700);
  font-size: 14px;
  margin-bottom: 16px;
}

.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.cancel-btn, .submit-btn {
  padding: 8px 20px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.cancel-btn {
  background: transparent;
  border: var(--border);
  color: var(--color-black);
}

.submit-btn {
  background: var(--color-black);
  border: none;
  color: var(--color-white);
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.datasources-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.datasource-card {
  background: var(--color-gray-50);
  border: var(--border);
  border-radius: var(--radius-lg);
  padding: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.datasource-name {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--color-black);
}

.datasource-type {
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
}

.badge-mysql { background: #00758f; color: white; }
.badge-postgresql { background: #336791; color: white; }
.badge-oracle { background: #f80000; color: white; }
.badge-gauss { background: #e30513; color: white; }
.badge-csv { background: #28a745; color: white; }
.badge-xlsx { background: #217346; color: white; }

.datasource-status {
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
}

.status-active { background: var(--color-green-500); color: white; }
.status-inactive { background: var(--color-warning); color: black; }
.status-error { background: var(--color-error); color: white; }
.status-disabled { background: var(--color-gray-400); color: white; }
.status-unknown { background: var(--color-gray-200); color: black; }

.header-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 6px 12px;
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.test-btn:hover { background: var(--color-info); color: white; }
.toggle-btn:hover { background: var(--color-warning); color: black; }
.delete-btn:hover { background: var(--color-error); color: white; }

.card-content {
  padding-top: 12px;
  border-top: var(--border-light);
}

.datasource-info {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.info-item {
  font-size: 13px;
}

.info-label {
  font-weight: 500;
  color: var(--color-gray-600);
  margin-right: 8px;
}

.info-value {
  color: var(--color-black);
}

.error-info {
  grid-column: 1 / -1;
}

.error-text {
  color: var(--color-error);
}

.delete-confirm {
  margin-top: 12px;
  padding: 12px;
  background: var(--color-red-50);
  border: 1px solid var(--color-red-200);
  border-radius: var(--radius-md);
}

.confirm-message {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: var(--color-red-700);
}

.confirm-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.delete-confirm-btn {
  padding: 6px 16px;
  background: var(--color-error);
  color: white;
  border: none;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.empty-state {
  padding: 60px 20px;
  text-align: center;
  color: var(--color-gray-500);
}

.empty-state p {
  margin-bottom: 16px;
  font-size: 16px;
}
</style>
