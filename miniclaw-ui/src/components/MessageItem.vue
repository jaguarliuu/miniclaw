<script setup lang="ts">
import type { Message } from '@/types'

defineProps<{
  message: Message
}>()
</script>

<template>
  <article class="message" :class="message.role">
    <div class="message-inner">
      <div class="message-meta">
        <span class="role">{{ message.role === 'user' ? 'You' : 'Assistant' }}</span>
      </div>
      <div class="message-content">
        <p v-for="(paragraph, i) in message.content.split('\n\n')" :key="i">
          {{ paragraph }}
        </p>
      </div>
    </div>
  </article>
</template>

<style scoped>
.message {
  padding: 24px 0;
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
  margin-bottom: 8px;
}

.role {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
}

.message-content {
  font-size: 15px;
  line-height: 1.7;
}

.message-content p {
  margin-bottom: 1em;
}

.message-content p:last-child {
  margin-bottom: 0;
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
  color: var(--color-gray-dark);
}

/* Assistant messages - full width, prominent */
.message.assistant .message-content {
  font-weight: 400;
}
</style>
