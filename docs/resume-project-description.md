# 简历项目描述建议

## 项目名称

AI 客户运营工单 Agent 系统

## 技术架构

Spring Boot, MySQL, Vue 3, FastAPI, LangGraph, Qdrant, DeepSeek API

## 项目描述

面向电商 CRM 的 AI 工单辅助系统。在客户、订单、商品和 RFM 分层能力基础上，引入独立 AI 服务，通过可观测 Agent Workflow 完成意图识别、客户画像聚合、知识检索、回复规划、风险检查和话术生成，辅助客服处理咨询、投诉和售后工单。

## 推荐职责表述

- 将 CRM 业务系统与 FastAPI AI 服务解耦，设计 `Intent Classifier -> Customer Profiler -> Knowledge Retriever -> Reply Planner -> Risk Checker -> Final Composer` 工作流，并在前端展示节点轨迹、召回证据和风险提示。
- 基于在售商品、FAQ 和售后政策构建检索增强链路，支持轻量检索和 Qdrant 本地向量检索；在无可靠商品证据时阻止模型生成推荐，降低无依据回复风险。
- 聚合客户 RFM 标签、历史订单和工单记录，生成差异化服务策略；对高价值客户、投诉、售后等场景设置不同回复规划和人工确认要求。
- 增加超时、异常降级和模板 fallback，确保 Python AI 服务或 LLM API 不可用时 CRM 工单主流程仍可继续处理。
- 构建 20 条离线工单回归集，覆盖推荐、投诉、售后、活动咨询和无匹配商品等场景，用于验证意图识别、结构化输出和风险护栏。

## 面试讲述边界

- 可以说：这是一个可观测的 Agent Workflow，有 LangGraph 编排、RAG 检索、业务护栏、服务降级和小规模离线评测。
- 不要说：完全消除幻觉、生产级高并发、复杂多智能体协作、已经在线服务真实用户。
