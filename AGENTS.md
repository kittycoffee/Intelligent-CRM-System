# Customer-ai-biz Agent Instructions

## Project Context

This is the intelligent CRM + AI Agent project.

- Spring Boot backend: `src/`
- FastAPI AI service: `ai-service/`
- Vue frontend: `frontend/`
- Obsidian project memory: `D:\Obsidian-Base\2026KnowledgeBase`

When making meaningful project changes, keep the implementation and the Obsidian project memory aligned.

## Daily Work Log

完成有意义的任务后，将简短记录追加到：

```text
D:\Obsidian-Base\2026KnowledgeBase\Inbox\codex-worklog\YYYY-MM-DD.md
```

每条记录包含：

- 项目名称
- 完成事项
- 涉及的重要文件
- 验证结果
- 未完成事项（如有）

推荐格式：

```markdown
## Customer-ai-biz
- 完成事项：修复登录过期后无法刷新 token 的问题。
- 修改：src/auth/session.ts、src/auth/session.test.ts
- 验证：npm test 通过。
- 未完成：补充端到端登录回归。
```

规则：

- 记录要轻量，不写完整 Git log。
- 优先写人能看懂的设计级摘要。
- 如果只做了调研、排查或文档阅读，也要记录“结论 / 下一步”。
- 如果没有完成验证，要写清楚“未验证”和原因。
- 晚上由 `daily-summary` Skill 统一读取 Inbox 并写入 Obsidian Daily Note。

## Obsidian Sync

当改动涉及 CRM、RFM、AI Agent、RAG、FastAPI AI 服务、Spring Boot、Vue、MySQL 或面试表达时，优先同步到：

- `D:\Obsidian-Base\2026KnowledgeBase\04_CRM-AI-Project\Changelog.md`
- `D:\Obsidian-Base\2026KnowledgeBase\06_Codex-Worklog\YYYY-MM.md`
- 必要时同步 `04_CRM-AI-Project\AI-Service.md`、`RAG-Design.md`、`Agent-Workflow.md`、`RFM-Module.md`、`Architecture.md`、`Module-Map.md`

同步时只写设计级摘要，不要把完整 Git log 堆进 Obsidian。
