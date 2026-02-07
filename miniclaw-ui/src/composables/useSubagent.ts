import { useWebSocket } from './useWebSocket'

const { request } = useWebSocket()

/**
 * SubAgent RPC 操作
 */

export interface SubagentListItem {
  subRunId: string
  subSessionId: string
  parentRunId: string
  requesterSessionId: string
  agentId: string
  status: string
  task: string
  deliver: boolean
  createdAt: string
  updatedAt?: string
}

/**
 * 列出子代理（按父运行 ID 或会话 ID）
 */
async function listSubagents(params: { parentRunId?: string; sessionId?: string }): Promise<SubagentListItem[]> {
  const result = await request<{ subagents: SubagentListItem[] }>('subagent.list', params)
  return result.subagents
}

/**
 * 停止子代理运行
 */
async function stopSubagent(subRunId: string): Promise<{ stopped: boolean }> {
  return await request<{ stopped: boolean; subRunId: string }>('subagent.stop', { subRunId })
}

/**
 * 向子代理发送消息
 */
async function sendToSubagent(subSessionId: string, message: string): Promise<{ newRunId: string }> {
  return await request<{ newRunId: string; subSessionId: string; queued: boolean }>(
    'subagent.send',
    { subSessionId, message }
  )
}

export function useSubagent() {
  return {
    listSubagents,
    stopSubagent,
    sendToSubagent
  }
}
