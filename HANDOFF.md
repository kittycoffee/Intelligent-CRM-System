# AI 客户运营工单 Agent 系统阶段交接

更新时间：2026-06-02  
分支：`master`  
远端：`origin/master`  
最新已推送提交：`5b91fca fix: scope campaigns to matching work order context`

## 1. 当前阶段结论

项目已经从“CRM 接入大模型 API”升级为具有 MySQL 结构化事实、Qdrant 文档检索、LangGraph 工作流、证据充分度、风险提示和前端执行轨迹的 AI 工单辅助系统。

但当前版本仍然存在一个核心缺陷：默认回复由 Python 固定模板生成，没有调用 LLM 对证据进行自然语言整合。因此系统目前“证据链可展示、工程结构较完整”，但回复仍然偏机械，部分商品咨询无法直接解决客户问题。

下一阶段优先级不是继续增加 UI，而是重构回复生成链路：使用阿里云百炼 `qwen3.6-plus`，让模型在 MySQL 和 Qdrant 证据约束内生成自然、具体、可直接使用的回复草稿。

## 2. 已完成内容

### 2.1 数据模型

- 新增 `promotion_campaign`：活动名称、适用客户、适用品类、优惠金额、有效期、`allowed_reply_text`。
- 新增 `service_entitlement`：服务权益、SLA、可承诺边界、`requires_manual_approval`、`max_promise_level`。
- 扩展 `cust_rfm_snapshot`：
  - `value_tier`：`high / normal`
  - `lifecycle_risk`：`active / silent / churn_risk`
- 扩展 `cust_interaction`：
  - `detected_intent`
  - `intent_override`
  - `agent_result_json`
  - `evidence_sufficiency`
- 已编写增量迁移脚本并在本机 MySQL `customer_ai` 数据库执行。

### 2.2 客户策略

- 将原来的单一 RFM 客户等级拆分为两个正交维度：
  - 客户价值：高价值或普通。
  - 生命周期风险：活跃、沉默或流失风险。
- 当前采用演示级简化规则，不宣称完整商业建模：
  - 累计消费金额达到 `5000` 元，或订单数达到 `5` 单时为高价值客户。
  - 超过 `30` 天未购买为沉默客户。
  - 超过 `60` 天未购买，且最近 `30` 天存在投诉、退款或明显负面工单时为流失风险客户。

### 2.3 文档库与 Qdrant

- 新增 `12` 个匿名化商城手册切片，覆盖：
  - 七天无理由退货
  - 商品质量问题与换货
  - 发货延迟
  - 已签收但未收到
  - 退款到账周期
  - 投诉升级
  - 高价值客户服务边界
  - 优惠券通用规则
  - 价格保护
  - 商品咨询 FAQ
- Qdrant 已从“每次请求临时创建内存库”改为显式导入、持久化复用。
- 默认 Embedding 模型改为 `BAAI/bge-small-zh-v1.5`。
- 默认采用关键词优先、Qdrant 补充的混合检索。
- 本机已验证：

```text
python -B ingest_handbook.py
indexed 12 handbook chunks into crm_handbook_chunks
```

查询：

```text
退款什么时候到账
```

首条语义命中：

```text
refund_timeline_v1_01 score=0.7743
```

### 2.4 Agent 工作流

当前已实现 LangGraph 七节点工作流：

```text
Intent Classifier
-> Customer Strategy Profiler
-> Offer & Entitlement Resolver
-> Handbook Retriever
-> Reply Planner
-> Risk Checker
-> Final Composer
```

已实现：

- 商品、活动、权益和政策证据区分来源。
- MySQL 负责商品库存、活动资格、优惠金额和服务权益。
- Qdrant 负责售后手册、物流说明和 FAQ 文档检索。
- `internal_actions` 与 `reply_draft` 分离。
- 删除误导性的置信度百分比，改为：
  - `sufficient`
  - `partial`
  - `insufficient`
- Java 将意图、证据充分度和 Agent JSON 持久化回工单。
- 支持人工修正意图后重新生成。

### 2.5 前端交互

- 录入新工单时取消手动选择“咨询 / 投诉”，只记录客户原始反馈。
- 工单详情页新增：
  - 自动意图与人工修正
  - 客户策略标签
  - Agent 执行轨迹
  - 可用活动
  - 服务权益
  - 手册证据
  - 客服内部动作
  - 风险提示
  - 推荐话术
- 已修复活动范围泄漏：投诉工单不会混入无关的“数码焕新活动”。

## 3. 当前实际工作流

当前线上代码的真实流程如下：

1. Spring Boot 查询客户、RFM、订单、商品、活动、权益和历史工单。
2. Spring Boot 将上下文发送到 FastAPI。
3. Python 使用关键词规则识别意图。
4. Python 根据客户策略筛选 MySQL 活动和权益。
5. Python 使用本地商品列表做轻量关键词匹配。
6. Python 使用关键词和 Qdrant 检索手册文档。
7. Python 生成内部动作和风险提示。
8. Python 使用固定模板拼接 `reply_draft`。
9. Spring Boot 持久化结果，前端展示。

最重要的事实：

```text
当前 Final Composer 没有调用 LLM。
```

模板代码位于：

```text
ai-service/agent_core.py -> compose_reply()
```

因此当前回复“稳定但机械”，只能作为过渡版本。

## 4. 已知问题

### 4.1 回复生硬，缺少自然语言整合

当前回复通过固定模板拼接，容易反复出现：

```text
我们会先核实……
我们已经记录……
我们会结合业务规则……
```

需要由 LLM 在证据边界内重新组织回复。

### 4.2 商品咨询意图识别不足

当前分类器主要依赖关键词：

```text
推荐、想买、有没有、适合、商品、价格、库存
```

输入：

```text
最近想入手一台游戏机，是买 Switch 还是别的？有现货吗？
```

可能误判为 `general_service`，从而返回泛化兜底话术。

### 4.3 Qdrant 文档只展示，没有形成明确政策结论

当前系统能够召回退货规则，但回答仍然可能写成：

```text
如果商品符合条件，可以申请退货。
```

更合理的回答应明确引用已召回规则：

```text
签收后七日内、包装和配件完整且不影响二次销售时，可以申请无理由退货。
```

然后只对缺失信息提示核实，例如签收时间和商品状态。

### 4.4 活动仅展示，未形成完整交互闭环

活动已从 MySQL 正确查询并展示，但缺少客服选择动作。

已经确认的产品决策：

```text
活动采用“客服勾选后写入话术”。
```

即：

- 未勾选活动：只作为内部候选项展示。
- 客服勾选活动并重新生成：LLM 将活动自然写入对客话术。
- 投诉和售后默认不勾选，避免强行营销。

### 4.5 订单上下文不足

当前 `order_info` 缺少履约和退款字段，导致系统面对发货、签收和退款问题时只能说“核实”。

已经确认的产品决策：

```text
补充演示级订单履约字段。
```

## 5. 待实施计划：Qwen3.6-Plus 重构

此部分为已经讨论但尚未实施的计划。不要误写为已完成功能。

### 5.1 替换模型供应商

全部移除 DeepSeek 命名，统一使用阿里云百炼：

```properties
DASHSCOPE_API_KEY=your_api_key
QWEN_API_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
QWEN_MODEL=qwen3.6-plus
QWEN_API_TIMEOUT_MS=30000
```

需要同步更新：

- 根目录 `.env.example`
- Spring Boot `application.properties`
- Java 旧版 AI 分析服务
- Python FastAPI Agent 服务
- README、AI 服务文档和简历描述

Python 新增统一 `QwenClient`：

- 自动加载项目根目录 `.env`
- JSON Mode 结构化输出
- `enable_thinking=false`
- 超时、JSON 解析、一次重试和模板降级

### 5.2 优化后的工作流

目标工作流：

```text
CRM Context Builder
-> Intent Router
-> Customer Strategy Profiler
-> Scenario Router
-> Structured Fact Resolver
-> Policy Retriever And Interpreter
-> Campaign Selection
-> Qwen Response Composer
-> Grounding Validator
-> Rewrite Or Fallback
```

节点职责：

1. `CRM Context Builder`
   - 查询客户、RFM、订单履约、商品、活动、权益和历史工单。

2. `Intent Router`
   - 优先使用客服人工修正。
   - 明确场景使用高精度规则。
   - 模糊场景调用 Qwen 结构化分类。
   - 新增 `sub_intent` 与 `intent_source`。

3. `Scenario Router`
   - 商品咨询：主要查商品、库存和价格。
   - 投诉：查订单状态、物流手册和权益。
   - 售后：查订单状态、退换货和退款规则。
   - 活动咨询：查有效活动和优惠券规则。

4. `Structured Fact Resolver`
   - 从 MySQL 获取商品、库存、价格、活动和权益。
   - 模型只能选择候选项，不能创造事实。

5. `Policy Retriever And Interpreter`
   - Qdrant 只召回当前意图相关文档。
   - 文档召回从 `5` 条减少到 `2-3` 条。
   - 手册切片增加：

```text
customer_safe_summary
required_checks
forbidden_promises
```

6. `Campaign Selection`
   - 接收客服勾选的 `selected_campaign_ids`。
   - 未勾选活动不得进入对客话术。

7. `Qwen Response Composer`
   - 输入客户问题、商品、订单、选中活动、权益、政策结论和禁止承诺项。
   - 输出：

```text
internal_actions
reply_draft
used_evidence_ids
```

8. `Grounding Validator`
   - 检查商品、库存、价格、活动、SLA 和政策结论是否都有 `evidence_id`。
   - 禁止虚构仓库原因、物流节点、优惠和赔偿。

9. `Rewrite Or Fallback`
   - 校验失败时调用 Qwen 重写一次。
   - 再失败时使用场景化模板。
   - 返回：

```text
generation_backend = qwen / qwen_rewrite / template_fallback
```

### 5.3 订单上下文扩展

计划扩展 `order_info`：

```text
fulfillment_status
logistics_status
shipped_time
signed_time
payment_method
refund_status
refund_apply_time
```

计划扩展 `cust_interaction`：

```text
related_order_id
```

录入工单时允许选择关联订单。

### 5.4 活动选择交互

计划在前端活动区域增加：

- 活动复选框。
- `推荐此活动` 快捷按钮。
- `使用选中活动重新生成` 按钮。

分析接口调整为 POST，并保留旧 GET 兼容：

```http
POST /interaction/analyze/{id}
```

请求：

```json
{
  "selectedCampaignIds": [2],
  "intentOverride": "product_consulting"
}
```

### 5.5 目标输出示例

商品咨询：

```text
您好，目前系统里有 Switch OLED 游戏掌机，库存充足，价格为 2099 元。如果您偏向便携和多人娱乐，这款比较合适。当前系统里暂时没有检索到其他游戏机型号，您也可以告诉我预算和常玩的游戏类型，我再帮您缩小范围。
```

售后退款：

```text
您好，商品在签收后七日内、包装和配件完整且不影响二次销售的情况下，可以申请无理由退货。您可以提供订单号，我先帮您确认签收时间。退款到账时间会受到支付渠道和银行处理进度影响，因此暂时无法承诺具体时间。
```

## 6. 剩余任务顺序

按模块提交并维护 `CHANGELOG.md`：

```text
feat: replace deepseek with qwen3.6-plus configuration
feat: add order fulfillment context for work orders
feat: add intent-aware policy guidance retrieval
feat: add selectable campaign reply generation
feat: generate grounded natural replies with qwen validation
feat: expose qwen generation and policy guidance in work order ui
test: expand reply quality regression scenarios
docs: update qwen workflow and resume description
```

## 7. 已修改的重要文件

### 数据库

```text
promotion_campaign.sql
service_entitlement.sql
migration_agent_strategy.sql
cust_rfm_snapshot.sql
cust_interaction.sql
demo_seed_agent.sql
```

### Python AI 服务

```text
ai-service/agent_core.py
ai-service/qdrant_retriever.py
ai-service/handbook.py
ai-service/ingest_handbook.py
ai-service/data/handbook_chunks.json
ai-service/data/eval_cases.json
ai-service/data/evaluation_report.json
ai-service/tests/test_agent_core.py
```

### Spring Boot

```text
src/main/java/com/candyd/customeraibiz/service/AiAgentWorkflowService.java
src/main/java/com/candyd/customeraibiz/controller/InteractionController.java
src/main/java/com/candyd/customeraibiz/service/impl/RfmAnalysisServiceImpl.java
```

### Vue

```text
frontend/src/views/CustomerDetailView.vue
frontend/src/views/InteractionView.vue
```

### 文档

```text
README.md
ai-service/README.md
CHANGELOG.md
docs/resume-project-description.md
```

## 8. 已执行测试

### Python 单元测试

```text
python -B -m unittest discover -s tests -v
5/5 passed
```

### 离线评测

```text
python -B run_evaluation.py
```

当前基线：

```json
{
  "total": 30,
  "passed": 30,
  "intent_accuracy": 1.0,
  "evidence_recall_rate": 1.0,
  "grounded_output_rate": 1.0,
  "unauthorized_commitment_count": 0,
  "json_schema_pass_rate": 1.0,
  "fallback_success_rate": 1.0
}
```

注意：这些指标来自离线演示回归集，不代表生产准确率。现有评测尚未覆盖自然语言质量。

### Spring Boot 编译

```text
mvn test -DskipTests
BUILD SUCCESS
```

### Vue 构建

```text
npm.cmd run build
build success
```

### 端到端验证

已在本机启动：

```text
Frontend:    http://127.0.0.1:5173
Backend:     http://127.0.0.1:8080
AI service:  http://127.0.0.1:8090
```

已验证高价值投诉工单：

- 识别为 `complaint`
- 策略为 `high + churn_risk`
- 活动仅保留 `老客复购专享券`
- Qdrant 命中 `发货延迟处理说明`
- 补偿资格标记为需要人工确认

## 9. 已推送提交

```text
74b3cc6 feat: add campaign entitlement and customer strategy schema
b1ea825 feat: add handbook documents and persistent hybrid retrieval
3162f76 feat: implement grounded differentiated agent workflow
79f8863 feat: integrate agent result persistence into crm work orders
29f906e feat: improve work order strategy ui
18dd8b7 docs: document hybrid retrieval and evidence boundaries
5b91fca fix: scope campaigns to matching work order context
```

## 10. 下一位开发者的第一步

优先完成 Qwen 配置与统一客户端，不要先改 UI：

1. 将 DeepSeek 配置全部替换为 `DASHSCOPE_API_KEY`、`QWEN_API_URL`、`QWEN_MODEL`。
2. 在 Python AI 服务新增 `QwenClient` 并加载根目录 `.env`。
3. 使用 Qwen JSON Mode 生成结构化回复。
4. 保留模板，仅作为 Qwen 不可用时的降级路径。
5. 用“游戏机咨询”“包装完整退货”“勾选活动后重新生成”三个场景做首轮验证。

