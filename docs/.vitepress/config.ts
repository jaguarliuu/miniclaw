import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'MiniClaw AI Agent 实战课程',
  description: '从零构建生产级 AI Agent 框架',
  lang: 'zh-CN',
  
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '课程大纲', link: '/outline' },
      { 
        text: '课程章节',
        items: [
          { text: '第0章 课程导学', link: '/chapters/00-intro' },
          { text: '第1章 AI Agent全景', link: '/chapters/01-agent-landscape' },
          { text: '第2章 实战应用', link: '/chapters/02-quick-start' },
          { text: '第3章 开发环境', link: '/chapters/03-environment' },
          { text: '第4章 LLM对接', link: '/chapters/04-llm-client' },
          { text: '第5章 WebSocket网关', link: '/chapters/05-websocket-gateway' },
          { text: '第6章 前端界面', link: '/chapters/06-frontend' },
          { text: '第7章 工具系统', link: '/chapters/07-tools' },
          { text: '第8章 ReAct循环', link: '/chapters/08-react-loop' },
          { text: '第9章 Skills系统', link: '/chapters/09-skills' },
          { text: '第10章 Memory系统', link: '/chapters/10-memory' },
          { text: '第11章 Cron自动化', link: '/chapters/11-cron' },
          { text: '第12章 MCP协议', link: '/chapters/12-mcp' },
          { text: '第13章 部署上线', link: '/chapters/13-deployment' },
          { text: '第14章 实战场景', link: '/chapters/14-use-cases' },
          { text: '第15章 架构师之路', link: '/chapters/15-architect' },
        ]
      }
    ],
    
    sidebar: {
      '/chapters/': [
        {
          text: '开始学习',
          items: [
            { text: '第0章 课程导学', link: '/chapters/00-intro' },
            { text: '第1章 AI Agent全景', link: '/chapters/01-agent-landscape' },
          ]
        },
        {
          text: '先用起来',
          items: [
            { text: '第2章 实战应用', link: '/chapters/02-quick-start' },
          ]
        },
        {
          text: '核心开发',
          items: [
            { text: '第3章 开发环境', link: '/chapters/03-environment' },
            { text: '第4章 LLM对接', link: '/chapters/04-llm-client' },
            { text: '第5章 WebSocket网关', link: '/chapters/05-websocket-gateway' },
            { text: '第6章 前端界面', link: '/chapters/06-frontend' },
          ]
        },
        {
          text: 'Agent能力',
          items: [
            { text: '第7章 工具系统', link: '/chapters/07-tools' },
            { text: '第8章 ReAct循环', link: '/chapters/08-react-loop' },
            { text: '第9章 Skills系统', link: '/chapters/09-skills' },
            { text: '第10章 Memory系统', link: '/chapters/10-memory' },
          ]
        },
        {
          text: '高级特性',
          items: [
            { text: '第11章 Cron自动化', link: '/chapters/11-cron' },
            { text: '第12章 MCP协议', link: '/chapters/12-mcp' },
            { text: '第13章 部署上线', link: '/chapters/13-deployment' },
          ]
        },
        {
          text: '实战与进阶',
          items: [
            { text: '第14章 实战场景', link: '/chapters/14-use-cases' },
            { text: '第15章 架构师之路', link: '/chapters/15-architect' },
          ]
        }
      ]
    },
    
    socialLinks: [
      { icon: 'github', link: 'https://github.com/jaguarliuu/jaguarclaw' }
    ],
    
    footer: {
      message: 'MiniClaw AI Agent 实战课程',
      copyright: 'Copyright © 2026 Jaguar Liu'
    },
    
    outline: {
      level: [2, 3]
    },
    
    search: {
      provider: 'local'
    }
  },
  
  markdown: {
    lineNumbers: true
  }
})
