import type { LlmProviderInput } from '@/types'

export interface ProviderPreset extends LlmProviderInput {
  id: string
  description?: string
}

export const providerPresets: ProviderPreset[] = [
  {
    id: 'deepseek',
    name: 'DeepSeek',
    endpoint: 'https://api.deepseek.com',
    apiKey: '',
    models: ['deepseek-chat', 'deepseek-reasoner'],
    description: 'DeepSeek AI'
  },
  {
    id: 'openai',
    name: 'OpenAI',
    endpoint: 'https://api.openai.com',
    apiKey: '',
    models: ['gpt-4o', 'gpt-4o-mini', 'o3-mini'],
    description: 'OpenAI GPT models'
  },
  {
    id: 'ollama',
    name: 'Ollama',
    endpoint: 'http://localhost:11434',
    apiKey: '',
    models: ['qwen2.5:7b', 'llama3:8b'],
    description: 'Local Ollama instance'
  },
  {
    id: 'qwen',
    name: '通义千问',
    endpoint: 'https://dashscope.aliyuncs.com/compatible-mode',
    apiKey: '',
    models: ['qwen-plus', 'qwen-turbo', 'qwen-max'],
    description: 'Alibaba Qwen'
  },
  {
    id: 'glm',
    name: 'GLM/智谱',
    endpoint: 'https://open.bigmodel.cn/api/paas/v4',
    apiKey: '',
    models: ['glm-4-flash', 'glm-4-plus', 'glm-4-long'],
    description: 'Zhipu AI GLM'
  },
  {
    id: 'minimax',
    name: 'MiniMax',
    endpoint: 'https://api.minimax.chat',
    apiKey: '',
    models: ['MiniMax-Text-01', 'abab6.5s-chat'],
    description: 'MiniMax AI'
  },
  {
    id: 'moonshot',
    name: 'Moonshot/月之暗面',
    endpoint: 'https://api.moonshot.cn',
    apiKey: '',
    models: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k'],
    description: 'Moonshot AI'
  },
  {
    id: 'doubao',
    name: '豆包/Doubao',
    endpoint: 'https://ark.cn-beijing.volces.com/api',
    apiKey: '',
    models: ['doubao-1.5-pro-32k', 'doubao-pro-32k'],
    description: 'ByteDance Doubao'
  },
  {
    id: 'baichuan',
    name: '百川/Baichuan',
    endpoint: 'https://api.baichuan-ai.com',
    apiKey: '',
    models: ['Baichuan4', 'Baichuan3-Turbo'],
    description: 'Baichuan AI'
  },
  {
    id: 'spark',
    name: '讯飞星火/Spark',
    endpoint: 'https://spark-api-open.xf-yun.com',
    apiKey: '',
    models: ['generalv3.5', '4.0Ultra'],
    description: 'iFlytek Spark'
  },
  {
    id: 'siliconflow',
    name: 'SiliconFlow',
    endpoint: 'https://api.siliconflow.cn',
    apiKey: '',
    models: ['deepseek-ai/DeepSeek-V3', 'Qwen/Qwen2.5-72B-Instruct'],
    description: 'SiliconFlow API'
  },
  {
    id: 'custom',
    name: '自定义',
    endpoint: '',
    apiKey: '',
    models: [],
    description: 'Custom OpenAI-compatible endpoint'
  }
]
