# CNB 文档

云原生构建文档
此文档在线地址: https://docs.cnb.cool/zh/llms.txt
## Table of Contents

### 指南

- [创建你的第一个仓库](/zh/guide/first-repo.md)
- [Git 地址与认证说明](/zh/guide/git-access.md)
- [迁移工具](/zh/guide/migration-tools.md)
- [角色权限](/zh/guide/role-permissions.md)
- [访问令牌](/zh/guide/access-token.md)
- [部署令牌](/zh/guide/deploy-key.md)

### 代码托管

- [产品介绍](/zh/repo/intro.md)
- [密钥仓库](/zh/repo/secret.md)
- [元数据](/zh/repo/annotations.md)
- [ISSUE 模板](/zh/repo/issue-template.md)
- [分类标签](/zh/repo/label.md)
- [UI 定制](/zh/repo/settings.md)

### 制品库

- [制品库介绍](/zh/artifact/intro.md)
- [Docker 制品库](/zh/artifact/docker.md)
- [Helm 制品库](/zh/artifact/helm.md)
- [Docker Model 制品库](/zh/artifact/docker-model.md)
- [Maven 制品库](/zh/artifact/maven.md)
- [npm 制品库](/zh/artifact/npm.md)
- [ohpm 制品库](/zh/artifact/ohpm.md)
- [Nuget 制品库](/zh/artifact/nuget.md)
- [Composer 制品库](/zh/artifact/composer.md)
- [PyPI 制品库](/zh/artifact/pypi.md)
- [Cargo 制品库](/zh/artifact/cargo.md)
- [Conan 制品库](/zh/artifact/conan.md)

### AI 助手

- [AI 助手简介](/zh/ai/intro.md)
- [知识库](/zh/ai/knowledge-base.md)
- [MCP Server](/zh/ai/mcp-server.md)
- [Code Wiki](/zh/ai/codewiki.md)

### 云原生开发

- [云原生开发介绍](/zh/workspaces/intro.md)
- [默认开发环境](/zh/workspaces/default-dev-env.md)
- [自定义开发环境](/zh/workspaces/custom-dev-env.md)
- [业务端口预览](/zh/workspaces/business-preview.md)
- [云原生开发工作区回收机制](/zh/workspaces/workspace-recycling.md)
- [代码备份和文件漫游](/zh/workspaces/file-keeper.md)
- [云原生开发推荐用法](/zh/workspaces/working-principle.md)
- [与云原生构建的区别](/zh/workspaces/workspace-vs-build.md)
- [常见问题解答](/zh/workspaces/question.md)
- [自定义环境创建流程](/zh/workspaces/custom-dev-pipeline.md)
- [自定义云原生开发启动按钮](/zh/workspaces/custom-dev-button.md)
- [单/双容器模式](/zh/workspaces/double-container.md)
- [使用技巧](/zh/workspaces/usage-tips.md)
- [仅预览模式](/zh/workspaces/only-preview.md)
- [VSCode/Cursor 客户端](/zh/workspaces/vscode-likes.md)
- [JetBrains 客户端](/zh/workspaces/jetbrains.md)
- [云原生开发 SSH 密钥指纹验证](/zh/workspaces/fingerprint.md)

### 云原生构建

- [云原生构建介绍](/zh/build/intro.md)
- [快速开始](/zh/build/quick-start.md)
- [触发规则](/zh/build/trigger-rule.md)
- [配置文件](/zh/build/configuration.md)
- [语法手册](/zh/build/grammar.md)
- [构建环境](/zh/build/build-env.md)
- [构建节点](/zh/build/build-node.md)
- [环境变量](/zh/build/env.md)
- [默认环境变量](/zh/build/build-in-env.md)
- [超时策略](/zh/build/timeout.md)
- [权限说明](/zh/build/permission.md)
- [内置任务](/zh/build/internal-steps.md)
- [插件市场](/zh/plugins.md)
- [插件制作](/zh/build/create-plugin.md)
- [贡献插件](/zh/build/contribute-plugin.md)
- [简化配置文件](/zh/build/simplify-configuration.md)
- [文件引用](/zh/build/file-reference.md)
- [流水线可视化](/zh/build/pipeline-visualization.md)
- [流水线缓存](/zh/build/pipeline-cache.md)
- [定时任务](/zh/build/crontab.md)
- [跳过流水线](/zh/build/skip-pipeline.md)
- [登录调试](/zh/build/login-debug.md)
- [手动触发流水线](/zh/build/web-trigger.md)
- [自定义部署流程](/zh/build/deploy.md)
- [NPC 事件](/zh/build/npc.md)
- [从 GitHub Actions 迁移到 CNB](/zh/build/migrate-to-cnb/migrate-from-github-actions.md)
- [构建环境](/zh/build/build-env.md)
- [默认环境变量](/zh/build/build-in-env.md)
- [配置文件](/zh/build/configuration.md)
- [贡献插件](/zh/build/contribute-plugin.md)
- [插件制作](/zh/build/create-plugin.md)
- [定时任务](/zh/build/crontab.md)
- [自定义部署流程](/zh/build/deploy.md)
- [环境变量](/zh/build/env.md)
- [文件引用](/zh/build/file-reference.md)
- [语法手册](/zh/build/grammar.md)
- [内置任务](/zh/build/internal-steps.md)
- [云原生构建介绍](/zh/build/intro.md)
- [登录调试](/zh/build/login-debug.md)
- [从 GitHub Actions 迁移到 CNB](/zh/build/migrate-to-cnb/migrate-from-github-actions.md)
- [NPC 事件](/zh/build/npc.md)
- [权限说明](/zh/build/permission.md)
- [流水线缓存](/zh/build/pipeline-cache.md)
- [流水线可视化](/zh/build/pipeline-visualization.md)
- [快速开始](/zh/build/quick-start.md)
- [脚本任务 vs 插件任务](/zh/build/script-vs-plugin.md)
- [构建Docker镜像并上传](/zh/build/showcache/docker-build-and-push-to-cnb-artifact.md)
- [AI 评审](/zh/build/showcase/ai-review.md)
- [ISSUE通知到企业微信群](/zh/build/showcase/issue-notice-group.md)
- [Monorepo按需构建](/zh/build/showcase/monorepo.md)
- [PR通知到企业微信群](/zh/build/showcase/pr-notice-group.md)
- [简化配置文件](/zh/build/simplify-configuration.md)
- [跳过流水线](/zh/build/skip-pipeline.md)
- [超时策略](/zh/build/timeout.md)
- [触发规则](/zh/build/trigger-rule.md)
- [手动触发流水线](/zh/build/web-trigger.md)
- [plugin](/zh/plugin/index.md)
- [插件市场](/zh/plugins.md)

### 开发者

- [徽章](/zh/develops/badge.md)
- [Open API](/zh/develops/openapi.md)
- [SKILLS](/zh/develops/skills.md)

### oauth 授权

- [如何使用 OAuth 授权](/zh/oauth/user.md)
- [创建 OAuth 应用](/zh/oauth/developer.md)

### Others

- [仓库动态](/zh/develops/openapi-event.md)
- [云原生构建-企业版](/zh/enterprise.md)
- [FAQ 常见问题](/zh/faq.md)
- [Logo](/zh/logo.md)
- [任务集介绍](/zh/missions/intro.md)
- [定价](/zh/pricing.md)
- [关于我们](/zh/saas/about.md)
- [云原生构建隐私保护声明](/zh/saas/privacy.md)
- [云原生构建服务等级协议](/zh/saas/sla.md)
- [TAPD](/zh/saas/tapd.md)
- [云原生构建服务协议](/zh/saas/terms.md)
- [平台赞赏](/zh/sponsor.md)
