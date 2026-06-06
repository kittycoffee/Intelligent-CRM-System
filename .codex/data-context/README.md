# Customer-ai-biz Reusable Data Context

This folder is the shared starting point for future data work on the intelligent CRM + AI Agent project.

Use it to keep stable context in one place before asking Codex or the Data Analytics plugin to analyze data, build reports, design KPIs, or diagnose metric movement.

## What To Keep Here

- `sources.yml`: known databases, CSV exports, APIs, and owners.
- `metrics.yml`: canonical business and product metrics.
- `entities.yml`: core CRM, RFM, AI Agent, and RAG entities.
- `questions.md`: recurring analysis questions and decision prompts.
- `data-contracts.md`: expected grains, keys, freshness, and quality checks.

## How To Use In Future Requests

Reference this folder when asking for analytics work:

```text
Use .codex/data-context as project context. Analyze <question> using <source>.
```

When a new dataset, metric, or validated insight is discovered, update the relevant file so future analysis starts with the same assumptions.

