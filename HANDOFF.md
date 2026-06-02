# AI 客户运营工单 Agent 系统阶段交接

更新时间：2026-06-02  
分支：`master`  
远端：`origin/master`  
最新已推送提交：`1ec32ad feat: add grounded qwen work-order generation`

## 1. 当前阶段结论

系统已完成 Qwen3.6-Plus 回复链路重构。当前版本不再默认使用固定模板拼接回复，而是由阿里云百炼 `qwen3.6-plus` 在 MySQL 结构化事实和 Qdrant 手册证据边界内生成自然语言草稿。

模板仍然保留，但只用于 Qwen 不可用、结构化输出无效或 grounding 校验失败后的降级路径。

当前已在本机真实验证：

- 工单页面可正常加载。
- 客服勾选活动后可以重新生成回复。
- 页面展示 `生成来源：Qwen3.6-Plus`。
- 生成回复自然包含客服选中的活动话术。
- 返回 `used_evidence_ids`，其中包含活动证据 ID。
- 未选活动不会进入对客回复。

## 2. 当前运行状态

本机开发服务正在运行：

```text
Frontend:    http://127.0.0.1:5173
Backend:     http://127.0.0.1:8080
AI service:  http://127.0.0.1:8090
```

健康检查已通过：

```text
GET http://127.0.0.1:8090/health
{"status":"ok"}

GET http://127.0.0.1:8080/interaction/pending
HTTP 200
```

Windows 本地开发环境必须优先使用 `127.0.0.1`，不要将 Java 到 FastAPI 的默认地址改回 `localhost`。本机曾出现 Java 将 `localhost` 解析到 IPv6 后连接拒绝的问题。

## 3. 已完成能力

### 3.1 Qwen 统一客户端

新增：

```text
ai-service/qwen_client.py
```

已实现：

- 自动加载项目根目录 `.env`。
- 使用阿里云百炼 OpenAI 兼容接口。
- 默认模型：`qwen3.6-plus`。
- JSON Mode：`response_format={"type":"json_object"}`。
- 关闭思考输出：`enable_thinking=false`。
- 请求超时、JSON 解析、一次重试。
- Qwen 不可用时模板降级。

配置：

```properties
DASHSCOPE_API_KEY=your_api_key
QWEN_API_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
QWEN_MODEL=qwen3.6-plus
QWEN_API_TIMEOUT_MS=30000
```

### 3.2 Agent 工作流

LangGraph 仍采用七节点编排：

```text
Intent Classifier
-> Customer Strategy Profiler
-> Offer & Entitlement Resolver
-> Handbook Retriever
-> Reply Planner
-> Risk Checker
-> Final Composer
```

节点内部职责已扩展：

- 意图路由优先使用客服人工修正，其次使用明确规则，模糊场景可调用 Qwen 结构化分类。
- 商品、活动、权益、订单履约和政策文档均区分证据来源。
- 手册检索按意图过滤，最多召回 `3` 条相关文档。
- 手册文档可输出政策摘要、待核实项和禁止承诺项。
- Final Composer 使用 Qwen JSON Mode 输出：

```text
internal_actions
reply_draft
used_evidence_ids
```

- 返回生成来源：

```text
generation_backend = qwen / qwen_rewrite / template_fallback
```

### 3.3 Grounding 校验

当前校验器会拦截：

- 未勾选活动进入客户回复。
- 客服已勾选活动但 Qwen 漏写活动话术。
- 活动进入回复但缺少对应活动证据 ID。
- 越权承诺退款、赔偿或具体到账时间。
- 客服内部动作原样混入客户回复。
- Qwen 返回未知证据 ID。

校验失败时：

1. 将违规原因交给 Qwen 重写一次。
2. 再失败时使用场景模板。

### 3.4 活动选择闭环

活动采用“客服勾选后写入话术”的产品规则：

- 未勾选活动：只作为内部候选项展示。
- 勾选活动：传递 `selectedCampaignIds`，Qwen 必须自然写入客户回复。
- 投诉和售后：默认不勾选活动，避免强行营销。

接口：

```http
POST /interaction/analyze/{id}
```

请求示例：

```json
{
  "selectedCampaignIds": [1],
  "intentOverride": "product_consulting"
}
```

旧 GET 接口仍保留兼容。

### 3.5 订单履约上下文

已新增迁移：

```text
migration_order_context.sql
```

`order_info` 新增：

```text
fulfillment_status
logistics_status
shipped_time
signed_time
payment_method
refund_status
refund_apply_time
```

`cust_interaction` 新增：

```text
related_order_id
```

本机 MySQL `customer_ai` 已执行迁移。前端录入工单时已支持选择关联订单。

### 3.6 Qdrant 手册检索

- 保留 `12` 个匿名化商城手册切片。
- 默认 Embedding 模型：`BAAI/bge-small-zh-v1.5`。
- 默认采用关键词优先、Qdrant 补充的混合检索。
- Qdrant 客户端查询后显式关闭，避免本地 SQLite 资源泄漏。
- 本机已重新执行：

```text
python -B ingest_handbook.py
indexed 12 handbook chunks into crm_handbook_chunks
```

### 3.7 前端展示

工单详情页当前展示：

- 自动意图和人工修正。
- 客户价值等级与生命周期风险。
- Agent 执行轨迹。
- 候选活动复选框和“使用选中活动重新生成”按钮。
- 命中商品、库存和价格。
- 服务权益。
- 手册证据。
- 政策结论与待核实项。
- 客服内部动作。
- 风险提示。
- 推荐话术。
- 生成来源：Qwen、Qwen 重写后通过或模板降级。

## 4. 数据与配置状态

本机已执行：

```text
migration_agent_strategy.sql
migration_order_context.sql
python -B ingest_handbook.py
```

根目录 `.env` 为本地私密配置，已被 `.gitignore` 忽略，不得提交。

简历私稿 `docs/resume-project-description.md` 已从当前仓库删除，并加入 `.gitignore`。历史提交中仍保留旧版本；如需彻底清除，需要单独执行 Git 历史重写和强推。

## 5. 已执行验证

### Python 单元测试

```text
cd ai-service
python -B -m unittest discover -s tests -v
13/13 passed
```

覆盖：

- 游戏机咨询意图。
- 商品证据和模板降级。
- 未选活动不泄漏。
- 选中活动写入回复。
- Qwen 漏写选中活动时触发重写。
- Qwen 泄漏未选活动时触发重写。
- 退货政策直接回答。
- Qwen JSON Mode 和一次重试。

### 离线评测

```text
cd ai-service
python -B run_evaluation.py
```

当前基线：

```json
{
  "total": 34,
  "passed": 34,
  "intent_accuracy": 1.0,
  "evidence_recall_rate": 1.0,
  "grounded_output_rate": 1.0,
  "unauthorized_commitment_count": 0,
  "json_schema_pass_rate": 1.0,
  "fallback_success_rate": 1.0,
  "direct_answer_rate": 1.0,
  "policy_usage_rate": 1.0,
  "selected_campaign_inclusion_rate": 1.0,
  "unselected_campaign_leak_count": 0,
  "irrelevant_evidence_count": 0
}
```

注意：

- 离线评测故意禁用真实 API Key，因此报告中的 `qwen_generation_success_rate=0.0`。
- 这些指标来自演示级回归集，不代表生产准确率。
- 真实 Qwen 已通过本地端到端验证。

### Java 和 Vue

```text
mvn test -DskipTests
BUILD SUCCESS

cd frontend
npm.cmd run build
build success
```

Vue 构建仍有单个 bundle 超过 `500 kB` 的提示，不影响构建成功。

### 真实端到端验证

已验证演示工单 `interaction_id=27`：

- 工单类型：商品咨询。
- 候选活动：`老客复购专享券`。
- 客服勾选后重新生成成功。
- `generation_backend=qwen`。
- `selected_campaigns` 数量为 `1`。
- `used_evidence_ids` 包含 `campaign_1`。
- 回复包含活动允许话术。
- 回复不包含 Markdown 加粗符号。
- 页面展示 `生成来源：Qwen3.6-Plus`。

## 6. 已推送提交

```text
74b3cc6 feat: add campaign entitlement and customer strategy schema
b1ea825 feat: add handbook documents and persistent hybrid retrieval
3162f76 feat: implement grounded differentiated agent workflow
79f8863 feat: integrate agent result persistence into crm work orders
29f906e feat: improve work order strategy ui
18dd8b7 docs: document hybrid retrieval and evidence boundaries
5b91fca fix: scope campaigns to matching work order context
10d2e1d docs: add crm agent phase handoff
1ec32ad feat: add grounded qwen work-order generation
```

## 7. 重要文件

### 数据库

```text
migration_agent_strategy.sql
migration_order_context.sql
promotion_campaign.sql
service_entitlement.sql
cust_interaction.sql
order_info.sql
demo_seed_agent.sql
```

### Python AI 服务

```text
ai-service/qwen_client.py
ai-service/agent_core.py
ai-service/app.py
ai-service/qdrant_retriever.py
ai-service/data/handbook_chunks.json
ai-service/data/eval_cases.json
ai-service/data/evaluation_report.json
ai-service/tests/test_agent_core.py
ai-service/tests/test_qwen_client.py
```

### Spring Boot

```text
src/main/java/com/candyd/customeraibiz/service/AiAgentWorkflowService.java
src/main/java/com/candyd/customeraibiz/service/AiMarketingService.java
src/main/java/com/candyd/customeraibiz/controller/InteractionController.java
src/main/java/com/candyd/customeraibiz/entity/OrderInfo.java
src/main/java/com/candyd/customeraibiz/entity/CustInteraction.java
src/main/resources/application.properties
```

### Vue

```text
frontend/src/views/CustomerDetailView.vue
frontend/src/views/InteractionView.vue
```

## 8. 剩余任务

建议下一阶段按以下顺序推进：

1. 为演示订单补充更完整的履约、签收和退款状态种子数据，验证关联订单问答。
2. 增加订单状态场景回归：未发货、运输中、已签收未收到、退款处理中。
3. 扩展 grounding 校验：进一步检查回复中的商品价格、库存和 SLA 是否逐项对应证据。
4. 优化商品匹配排序，减少宽泛关键词命中不相关商品。
5. 评估前端按路由拆分 bundle，降低单包体积。
6. 如需彻底移除 Git 历史中的简历私稿，单独安排历史重写和强推。

## 9. 下一位开发者的第一步

优先补充演示订单履约数据和关联订单回归，不要继续堆叠 UI。

推荐先验证：

```text
未发货订单 -> 使用真实 fulfillment_status，不虚构仓库原因
已签收未收到 -> 引用物流核查政策
退款处理中 -> 引用 payment_method 和 refund_status，不承诺具体到账时间
```
