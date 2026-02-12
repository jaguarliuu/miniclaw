<script setup lang="ts">
import { computed } from 'vue'
import type { Message } from '@/types'
import { useMarkdown } from '@/composables/useMarkdown'
import ToolCallCard from './ToolCallCard.vue'
import SkillActivationCard from './SkillActivationCard.vue'
import SubagentCard from './SubagentCard.vue'
import FileCard from './FileCard.vue'
import ContextChip from './ContextChip.vue'

const props = defineProps<{
  message: Message
  activeSubagentId?: string | null
}>()

const emit = defineEmits<{
  confirm: [callId: string, decision: 'approve' | 'reject']
  'select-subagent': [subRunId: string]
}>()

const { render } = useMarkdown()

// 是否有交错的 blocks（assistant 消息）
const hasBlocks = computed(() =>
  props.message.role === 'assistant' && props.message.blocks && props.message.blocks.length > 0
)

// 简单内容渲染（用于 user 消息或没有 blocks 的 assistant 消息）
const renderedContent = computed(() => render(props.message.content))

// 渲染文本块
function renderTextBlock(content: string | undefined): string {
  return render(content || '')
}

// 优先使用 attachedContexts，如果为空则向后兼容 attachedFiles
const displayContexts = computed(() => {
  if (props.message.attachedContexts && props.message.attachedContexts.length > 0) {
    return props.message.attachedContexts
  }
  // 向后兼容：如果 attachedFiles 存在，转换为 AttachedContext 格式
  if (props.message.attachedFiles && props.message.attachedFiles.length > 0) {
    return props.message.attachedFiles
  }
  return []
})
</script>

<template>
  <article class="message" :class="message.role">
    <div class="message-inner">
      <div class="message-meta">
        <span class="msg-avatar" :class="message.role">{{ message.role === 'user' ? 'U' : 'M' }}</span>
        <span class="role">{{ message.role === 'user' ? 'You' : 'MiniClaw' }}</span>
      </div>

      <!-- 有 blocks 的 assistant 消息：交错显示 -->
      <template v-if="hasBlocks">
        <template v-for="block in message.blocks" :key="block.id">
          <!-- Text block -->
          <div
            v-if="block.type === 'text' && block.content"
            class="message-content markdown-body"
            v-html="renderTextBlock(block.content)"
          ></div>

          <!-- Tool block -->
          <ToolCallCard
            v-else-if="block.type === 'tool' && block.toolCall"
            :tool-call="block.toolCall"
            :session-id="message.sessionId"
            @confirm="(callId, decision) => emit('confirm', callId, decision)"
          />

          <!-- Skill activation block -->
          <SkillActivationCard
            v-else-if="block.type === 'skill' && block.skillActivation"
            :activation="block.skillActivation"
          />

          <!-- SubAgent block -->
          <SubagentCard
            v-else-if="block.type === 'subagent' && block.subagent"
            :subagent="block.subagent"
            :active-subagent-id="activeSubagentId"
            @select="(subRunId) => emit('select-subagent', subRunId)"
          />

          <!-- File block -->
          <FileCard
            v-else-if="block.type === 'file' && block.file"
            :file="block.file"
            :session-id="message.sessionId"
          />
        </template>
      </template>

      <!-- 简单消息：直接显示内容 -->
      <template v-else>
        <!-- 用户消息附带的数据源和上下文 -->
        <div v-if="message.role === 'user' && (message.dataSourceName || displayContexts.length > 0)" class="user-contexts">
          <!-- 数据源 chip -->
          <div v-if="message.dataSourceName" class="datasource-chip-display">
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" class="chip-icon">
              <path d="M2 3C2 2.44772 2.44772 2 3 2H9C9.55228 2 10 2.44772 10 3V4C10 4.55228 9.55228 5 9 5H3C2.44772 5 2 4.55228 2 4V3Z" fill="currentColor"/>
              <path d="M2 8C2 7.44772 2.44772 7 3 7H9C9.55228 7 10 7.44772 10 8V9C10 9.55228 9.55228 10 9 10H3C2.44772 10 2 9.55228 2 9V8Z" fill="currentColor"/>
            </svg>
            <span class="chip-label">{{ message.dataSourceName }}</span>
          </div>
          <!-- 上下文 chips -->
          <ContextChip
            v-for="context in displayContexts"
            :key="context.id"
            :context="context"
            :readonly="true"
          />
        </div>
        <div class="message-content markdown-body" v-html="renderedContent"></div>
      </template>
    </div>
  </article>
</template>

<style scoped>
.message {
  padding: 20px 0;
  border-bottom: var(--border-light);
}

.message:last-child {
  border-bottom: none;
}

.message-inner {
  display: flex;
  flex-direction: column;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.role {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-gray-600);
}

.msg-avatar {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.msg-avatar.user {
  background: var(--color-gray-100);
  color: var(--color-gray-600);
}

.msg-avatar.assistant {
  background: var(--color-black);
  color: white;
}

.message-content {
  font-size: 15px;
  line-height: 1.7;
  padding-left: 30px;
}

/* User messages - right aligned, compact */
.message.user {
  display: flex;
  justify-content: flex-end;
}

.message.user .message-inner {
  max-width: 80%;
  align-items: flex-end;
}

.message.user .message-content {
  text-align: right;
  color: var(--color-gray-700);
}

.user-contexts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-left: 30px;
  margin-bottom: 6px;
  justify-content: flex-end;
}

.message.user .user-contexts {
  padding-left: 0;
}

.datasource-chip-display {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
}

.datasource-chip-display .chip-icon {
  opacity: 0.9;
  flex-shrink: 0;
}

.datasource-chip-display .chip-label {
  white-space: nowrap;
}

/* Assistant messages - full width, prominent */
.message.assistant .message-content {
  font-weight: 400;
}
</style>

<!-- Markdown styles (unscoped to apply to v-html content) -->
<style>
@import '@/styles/markdown.css';
</style>
