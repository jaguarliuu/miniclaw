import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'MiniClaw AI Agent 实战课程',
  description: '从零构建生产级 AI Agent 框架',
  lang: 'zh-CN',
  
  base: '/miniclaw/',
  
  ignoreDeadLinks: [
    /^http:\/\/localhost/,
    /^http:\/\/127\.0\.0\.1/,
  ],
  
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '课程大纲', link: '/outline' },
      { 
        text: '课程章节',
        items: [
          { text: '第0章 课程导学', link: '/chapters/00-intro' },
          { text: '第1章 AI Agent全景', link: '/chapters/01-overview' },
          { text: '第2章 实战应用', link: '/chapters/02-quick-start' },
          { 
            text: '第3章 开发环境', 
            items: [
              { text: '章节导航', link: '/chapters/03-00-chapter-index' },
              { text: '3.1 开发环境准备', link: '/chapters/03-01-dev-env' },
              { text: '3.2 Docker Compose', link: '/chapters/03-02-docker-compose' },
              { text: '3.3 Flyway 迁移', link: '/chapters/03-03-flyway' },
              { text: '3.4 Spring Boot 骨架', link: '/chapters/03-04-spring-boot' },
              { text: '3.5 配置管理', link: '/chapters/03-05-config' },
            ]
          },
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
          text: '📖 开始学习',
          collapsed: false,
          items: [
            { text: '第0章 课程导学', link: '/chapters/00-intro' },
            { text: '第1章 AI Agent全景', link: '/chapters/01-overview' },
          ]
        },
        {
          text: '🚀 先用起来',
          collapsed: false,
          items: [
            { text: '第2章 实战应用', link: '/chapters/02-quick-start' },
          ]
        },
        {
          text: '⚙️ 第3章 开发环境与基础底座',
          collapsed: false,
          items: [
            { text: '章节导航', link: '/chapters/03-00-chapter-index' },
            { text: '3.1 开发环境准备', link: '/chapters/03-01-dev-env' },
            { text: '3.2 一键启动：Docker Compose', link: '/chapters/03-02-docker-compose' },
            { text: '3.3 数据库版本控制：Flyway', link: '/chapters/03-03-flyway' },
            { text: '3.4 Spring Boot 项目骨架', link: '/chapters/03-04-spring-boot' },
            { text: '3.5 配置管理', link: '/chapters/03-05-config' },
          ]
        },
        {
          text: '🤖 核心开发',
          collapsed: true,
          items: [
            { text: '第4章 LLM对接', link: '/chapters/04-llm-client' },
            { text: '第5章 WebSocket网关', link: '/chapters/05-websocket-gateway' },
            { text: '第6章 前端界面', link: '/chapters/06-frontend' },
          ]
        },
        {
          text: '🛠️ Agent能力',
          collapsed: true,
          items: [
            { text: '第7章 工具系统', link: '/chapters/07-tools' },
            { text: '第8章 ReAct循环', link: '/chapters/08-react-loop' },
            { text: '第9章 Skills系统', link: '/chapters/09-skills' },
            { text: '第10章 Memory系统', link: '/chapters/10-memory' },
          ]
        },
        {
          text: '🔧 高级特性',
          collapsed: true,
          items: [
            { text: '第11章 Cron自动化', link: '/chapters/11-cron' },
            { text: '第12章 MCP协议', link: '/chapters/12-mcp' },
            { text: '第13章 部署上线', link: '/chapters/13-deployment' },
          ]
        },
        {
          text: '🎯 实战与进阶',
          collapsed: true,
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
