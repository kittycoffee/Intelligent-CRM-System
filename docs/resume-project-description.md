# 简历项目描述建议

## 项目名称

AI 客户运营工单 Agent 系统

## 技术架构

Spring Boot, MySQL, Vue 3, FastAPI, LangGraph, Qdrant, DeepSeek API

## 项目描述

面向电商 CRM 的 AI 工单辅助系统。在客户、订单、商品和简化 RFM 分层能力基础上，引入独立 AI 服务，通过有证据约束的 Agent Workflow 完成意图识别、客户策略分析、混合检索、回复规划和风险检查，辅助客服处理咨询、投诉、售后与活动咨询工单。

## 推荐职责表述

- 设计 `Intent Classifier -> Customer Strategy Profiler -> Offer & Entitlement Resolver -> Handbook Retriever -> Reply Planner -> Risk Checker -> Final Composer` 工作流，将客服内部动作与客户回复草稿分离，并支持人工修正意图后重新生成。
- 采用 MySQL 与 Qdrant 混合检索：从 MySQL 精确查询商品库存、有效活动和服务权益；对匿名化售后手册、物流说明和 FAQ 执行关键词优先、中文向量检索补充的 RAG 链路，避免无依据推荐。
- 使用简化 RFM 规则拆分客户价值等级和生命周期风险，为高价值投诉、售后快速通道、流失风险客户活动推荐等场景生成差异化处理策略。
- 增加证据充分度、越权承诺检查和异常降级机制，限制 AI 直接承诺优惠、退款和赔偿；Python 服务或 Qdrant 不可用时仍可继续处理工单。
- 构建 30 条离线场景回归集，统计意图识别准确率、证据召回率、结构化输出通过率、无依据承诺次数和降级成功率。

## 面试讲述边界

- 可以说：这是一个可观测的 Agent Workflow，有混合 RAG、业务证据约束、服务降级和小规模离线评测。
- 不要说：完全消除幻觉、生产级高并发、复杂多智能体协作、已经在线服务真实用户。

