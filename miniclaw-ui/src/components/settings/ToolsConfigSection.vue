<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useToolConfig } from '@/composables/useToolConfig'
import ConfigCard from '@/components/common/ConfigCard.vue'
import TrustedDomainsModal from './modals/TrustedDomainsModal.vue'
import SearchProvidersModal from './modals/SearchProvidersModal.vue'
import CommandSafetyModal from './modals/CommandSafetyModal.vue'

const { config, loading, error, getConfig, saveConfig } = useToolConfig()

// Modal state
const activeModal = ref<'domains' | 'providers' | 'safety' | null>(null)

// Save state
const saving = ref(false)
const saveError = ref<string | null>(null)
const saveSuccess = ref(false)

// Computed summaries for cards
const domainsSummary = computed(() => {
  if (!config.value) return ''
  const total = config.value.trustedDomains.defaults.length + config.value.trustedDomains.user.length
  return `${total} domains allowed (${config.value.trustedDomains.user.length} custom)`
})

const providersSummary = computed(() => {
  if (!config.value) return ''
  const enabled = config.value.searchProviders.filter(p => p.enabled).length + 1 // +1 for DuckDuckGo
  return `${enabled} providers enabled`
})

const safetySummary = computed(() => {
  if (!config.value) return ''
  const toolsCount = config.value.hitl?.alwaysConfirmTools?.length || 0
  const keywordsCount = config.value.hitl?.dangerousKeywords?.length || 0
  return `${toolsCount} tools, ${keywordsCount} keywords configured`
})

async function handleSaveDomains(domains: string[]) {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveConfig({
      userDomains: domains,
      searchProviders: config.value?.searchProviders.map(p => ({
        type: p.type,
        apiKey: '',
        enabled: p.enabled
      })) || [],
      hitl: {
        alwaysConfirmTools: Array.from(config.value?.hitl?.alwaysConfirmTools || []),
        dangerousKeywords: Array.from(config.value?.hitl?.dangerousKeywords || [])
      }
    })
    await getConfig()
    saveSuccess.value = true
    activeModal.value = null
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}

async function handleSaveProviders(providers: { type: string; apiKey: string; enabled: boolean }[]) {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveConfig({
      userDomains: Array.from(config.value?.trustedDomains.user || []),
      searchProviders: providers,
      hitl: {
        alwaysConfirmTools: Array.from(config.value?.hitl?.alwaysConfirmTools || []),
        dangerousKeywords: Array.from(config.value?.hitl?.dangerousKeywords || [])
      }
    })
    await getConfig()
    saveSuccess.value = true
    activeModal.value = null
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}

async function handleSaveSafety(data: { alwaysConfirmTools: string[]; dangerousKeywords: string[] }) {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveConfig({
      userDomains: Array.from(config.value?.trustedDomains.user || []),
      searchProviders: config.value?.searchProviders.map(p => ({
        type: p.type,
        apiKey: '',
        enabled: p.enabled
      })) || [],
      hitl: data
    })
    await getConfig()
    saveSuccess.value = true
    activeModal.value = null
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await getConfig()
})
</script>

<template>
  <div class="tools-section">
    <header class="section-header">
      <div>
        <h2 class="section-title">/tools</h2>
        <p class="section-subtitle">Built-in tool configuration</p>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && !config" class="loading-state">Loading configuration...</div>

    <!-- Error -->
    <div v-if="error && !config" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="getConfig">Retry</button>
    </div>

    <!-- Save Success -->
    <div v-if="saveSuccess" class="save-success">Configuration saved successfully</div>

    <!-- Save Error -->
    <div v-if="saveError" class="save-error">{{ saveError }}</div>

    <!-- Config Cards -->
    <template v-if="config">
      <div class="cards-grid">
        <ConfigCard
          title="HTTP Trusted Domains"
          description="Configure which domains are allowed for http_get requests"
          :summary="domainsSummary"
          @click="activeModal = 'domains'"
        />

        <ConfigCard
          title="Web Search Providers"
          description="Configure search engines for the web_search tool"
          :summary="providersSummary"
          @click="activeModal = 'providers'"
        />

        <ConfigCard
          title="Command Safety"
          description="Configure tools and commands requiring confirmation"
          :summary="safetySummary"
          :badge="{ text: 'Security', variant: 'warning' }"
          @click="activeModal = 'safety'"
        />
      </div>
    </template>

    <!-- Modals -->
    <TrustedDomainsModal
      v-if="activeModal === 'domains' && config"
      :default-domains="Array.from(config.trustedDomains.defaults)"
      :user-domains="Array.from(config.trustedDomains.user)"
      @close="activeModal = null"
      @save="handleSaveDomains"
    />

    <SearchProvidersModal
      v-if="activeModal === 'providers' && config"
      :providers="config.searchProviders.map(p => ({
        type: p.type,
        displayName: p.displayName,
        keyRequired: p.keyRequired,
        apiKey: p.apiKey,
        enabled: p.enabled,
        apiKeyUrl: p.apiKeyUrl || ''
      }))"
      @close="activeModal = null"
      @save="handleSaveProviders"
    />

    <CommandSafetyModal
      v-if="activeModal === 'safety' && config"
      :always-confirm-tools="Array.from(config.hitl?.alwaysConfirmTools || [])"
      :dangerous-keywords="Array.from(config.hitl?.dangerousKeywords || [])"
      @close="activeModal = null"
      @save="handleSaveSafety"
    />
  </div>
</template>

<style scoped>
.tools-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
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

.loading-state,
.error-state {
  padding: 40px;
  text-align: center;
  color: var(--color-gray-dark);
}

.retry-btn {
  margin-top: 12px;
  padding: 8px 16px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 13px;
}

.retry-btn:hover {
  background: var(--color-gray-bg);
}

.save-success {
  padding: 10px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-md);
  color: #166534;
  font-size: 13px;
  margin-bottom: 16px;
  max-width: 800px;
}

.save-error {
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
  margin-bottom: 16px;
  max-width: 800px;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  max-width: 1200px;
}

@media (max-width: 768px) {
  .cards-grid {
    grid-template-columns: 1fr;
  }
}
</style>
