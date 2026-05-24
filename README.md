# 🚀 基于 DeepSeek 与 Agentic RAG 的智能电商 CRM 系统

![Spring Boot](https://img.shields.io/badge/Spring_Boot-2.X-green.svg)
![Vue 3](https://img.shields.io/badge/Vue-3.x-blue.svg)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.X-orange.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.0+-blue.svg)
![DeepSeek](https://img.shields.io/badge/AI-DeepSeek_API-purple.svg)

> **面向中小电商的精细化运营与智能辅助决策平台**
> 本项目致力于解决传统 CRM 中“客户价值难洞察”与“大模型生成商品易产生数据幻觉”的业务痛点。通过底层的动态 RFM 规则引擎与创新的 Agentic RAG 工作流，实现“看人下菜碟”的精准服务及“零幻觉”的自动化客服生成。

---

## ✨ 核心业务特性 (Core Features)

### 1. 🎯 动态 RFM 客户价值规则引擎
* **内存级高效计算**：基于全量有效订单流水，通过 Java 8 Stream API 在内存中实现海量订单的按客分组、多维指标提炼。
* **三阶动态分层**：动态评定 `R(近度)`、`F(频度)`、`M(额度)` 原始数值，匹配自定义阈值库，将海量用户精准划分为**“重要价值客户”**、**“一般保持客户”**及**“流失风险客户”**。
* **数据快照沉淀**：自动清洗归集，定期将分层结果持久化至快照表 (`cust_rfm_snapshot`)，实现业务接口与大数据的底层解耦。

### 2. 🛡️ “零幻觉” Agentic RAG 客服工作流
* 彻底抛弃传统的大模型“纯向量检索”黑盒，首创**“意图提取 ➔ SQL 现货检索 ➔ 强约束生成”**的三步走混合架构。
* **物理级护栏**：AI 降维提取搜索词后，直接联动 MySQL 数据库执行 `status=1` (上架现货) 的精确检索，确保大模型生成的推荐话术 **100% 来源于真实有效库存**，彻底杜绝 AI 凭空捏造商品的商业级事故。

### 3. 🧠 大模型全息辅助管理
* **个体三段式诊断**：利用 Prompt 强约束指令，结合 RFM 客户画像与历史互动流水，为管理员输出纯文本格式的`【状态判断】`、`【价值评估】`、`【决策建议】`深度简报。
* **驾驶舱每日简报**：自动汇总当日全链路数据（销售额、待处理工单、高价值流失风险客户等），生成极简经营汇报，并融入系统级缓存策略 (按日触发)，大幅降低 API 成本及响应延迟。

---

## 🛠️ 技术栈 (Technology Stack)

### 后端架构
* **核心框架**：Spring Boot 2.X
* **ORM 层**：MyBatis-Plus (集成 `PaginationInnerInterceptor` 分页插件)
* **数据库**：MySQL 8.0 / HikariCP 连接池
* **AI 交互**：Spring 原生 `RestTemplate` + 自定义 Prompt 引擎

### 前端架构
* **核心框架**：Vue 3 + Vite
* **UI 组件库**：Element-Plus
* **网络请求**：Axios (统一封装全局 Token 拦截与错误校验)
* **权限路由**：基于 RBAC 模型的动态路由与按钮级权限控制 (`v-hasRole`)

---

## 📂 核心代码目录结构说明

```text
├── src/main/java/com/candyd/customeraibiz/
│   ├── controller/          # 视图控制层 (RESTful 接口暴露)
│   │   ├── ProductController.java    # 现货商品上下架与 CRUD
│   │   └── CustomerController.java   # 客户资料查询与维护
│   ├── service/             # 核心业务逻辑层
│   │   ├── AiMarketingService.java   # 💥 Agentic RAG 核心流转引擎 & AI 诊断逻辑
│   │   └── InteractionServiceImpl.java 
│   ├── mapper/              # MyBatis-Plus 数据访问层
│   ├── entity/              # 数据库实体类 (OrderInfo, ProductInfo 等)
│   └── utils/               # 系统通用工具类 (Result 封装等)
├── src/main/resources/
│   └── application.properties # 数据库连接与 AI API Key 配置
```

---

## 🚀 快速开始 (Getting Started)

### 1. 环境准备
确保您的本地环境已安装以下依赖：
* JDK 8 或以上版本
* Node.js (建议 16+)
* MySQL 8.0 数据库

### 2. 数据库配置
1. 在 MySQL 中新建名为 `customer_ai` 的数据库。
2. 导入提供的 SQL 初始化脚本（建立 `order_info`、`cust_rfm_snapshot`、`product_info` 等表结构）。
3. 修改 `application.properties` 中的数据库配置：
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/customer_ai?serverTimezone=GMT%2B8&characterEncoding=utf-8
   spring.datasource.username=root
   spring.datasource.password=您的密码
   ```

### 3. 配置 DeepSeek API
本项目依赖 DeepSeek 官方 API 作为大模型底座，请在 `application.properties` 中填入：
```properties
ai.api.url=https://api.deepseek.com/chat/completions
ai.api.key=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx # 请替换为您申请的 API Key
ai.model=deepseek-chat
```

### 4. 启动项目
**后端启动：**
找到 `CustomerAiBizApplication.java`（主启动类），右键 Run 启动 Spring Boot 容器 (默认端口 8080)。

**前端启动：**
进入 Vue 项目根目录，打开终端执行：
```bash
npm install     # 安装项目依赖
npm run dev     # 启动前端本地开发服务器
```

---

## 📜 核心算法说明 (Agentic RAG 工作流)

以下为本系统规避数据幻觉的底层逻辑实现伪代码摘要：

```text
算法 1: 基于大模型的防幻觉推荐流程
输入: 客户实时问诊文本 (userMessage)
输出: 可靠且尊贵的客服回复话术

aiKeyword ← 调用 DeepSeek 提取 userMessage 核心购物意图
如果 aiKeyword 为空，则降级为默认热搜词

products ← 数据库查询(SELECT * FROM product_info WHERE name LIKE aiKeyword AND status = 1)
// status = 1 物理拦截，保证查询到的绝对是有库存的现货

customerInfo ← 获取当前用户的 RFM 分级与姓名
promptContext ← 融合 "客户等级" + "真实 products 列表" + "用户留言"

finalDraft ← 调用 DeepSeek 施加三段式强约束生成最终语料

保存 finalDraft 至工单交互记录
```

---

## 🤝 贡献与感谢
感谢在系统落地过程中给予灵感与技术支持的开源社区。特别鸣谢 **Vue 团队**、**Spring 生态** 及 **DeepSeek 开放平台** 提供的高质量底座能力。

## 📝 许可 (License)
本项目为学术研究与毕业设计展示用途，代码开源并遵循 MIT License。
