package com.candyd.customeraibiz.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.*;
import com.candyd.customeraibiz.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiMarketingService {

    @Autowired private CustRfmSnapshotMapper snapshotMapper;
    @Autowired private AiAdviceHistoryMapper adviceMapper;
    @Autowired private CustInteractionMapper interactionMapper;
    @Autowired private CustomerInfoMapper customerInfoMapper;
    // 👇 新增：注入商品 Mapper，用于查库存
    @Autowired private ProductInfoMapper productMapper;

    // 👇👇👇 补上这一行 👇👇👇
    @Autowired private OrderInfoMapper orderMapper;

    @Value("${ai.api.url}") private String apiUrl;
    @Value("${ai.api.key}") private String apiKey;
    @Value("${ai.model}") private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private String dailyInsightCache = null;
    private LocalDate cacheDate = null; // 记录缓存是哪天的


    /**
     * 🔥 智能体步骤 1：让 AI 提取用户的商品搜索关键词（检索）
     */
    private String extractSearchKeywordFromAi(String userMessage) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("你是一个意图识别引擎。请从以下客户留言中，提取出1个最核心的商品搜索关键词（例如：羽绒服、手机、耳机）。\n");
            sb.append("规则1：如果客户没有表达购买意图，或者没有明确的商品指向，请直接返回一个字：无\n");
            sb.append("规则2：千万不要解释！千万不要带标点符号！只能输出这一个词语！\n");
            sb.append("客户留言：").append(userMessage);

            // 调用 DeepSeek
            String keyword = callDeepSeekApi(sb.toString());

            // 清理 AI 可能带上的多余换行或标点
            if (keyword != null) {
                keyword = keyword.replaceAll("[\\pP\\p{Punct}\\n\\r]", "").trim();
                log.info("🎯 AI 从留言中提取的关键词为：[{}]", keyword);
                return keyword;
            }
        } catch (Exception e) {
            log.error("AI 提取关键词失败", e);
        }
        return "无"; // 降级策略
    }


    /**
     * 🔥 核心升级：生成回复草稿 (RAG 检索增强版)
     * 结合了：客户RFM + 交互内容 + 【真实在售商品列表】
     */
    public void generateReplyDraft(CustInteraction interaction) {
        try {
            // 1. 查客户背景 (RFM + 姓名)
            CustRfmSnapshot rfm = snapshotMapper.selectOne(
                    new QueryWrapper<CustRfmSnapshot>().eq("cust_id", interaction.getCustId()).last("LIMIT 1")
            );
            CustomerInfo info = customerInfoMapper.selectById(interaction.getCustId());
            String name = (info != null) ? info.getCustName() : "客户";
            // 获取性别和价值标签
            String gender = (info != null && info.getGender() != null) ? info.getGender() : "未知";
            String level = (rfm != null) ? rfm.getCustomerLevel() : "普通客户";

            // 2. 🔥 核心升级：先让 AI 提取关键词，再去数据库做精准 RAG 检索
            String customerMessage = interaction.getContent();

            //使用上面检索关键词的函数（意图识别）
            String aiKeyword = extractSearchKeywordFromAi(customerMessage);

            // 护栏约束：状态必须为 1（已上架）
            QueryWrapper<ProductInfo> productQuery = new QueryWrapper<ProductInfo>().eq("status", 1);


            // 如果 AI 识别出了有效的关键词，去数据库里精准打捞
            if (!"无".equals(aiKeyword) && !aiKeyword.isEmpty()) {
                productQuery.and(wrapper -> wrapper
                        .like("product_name", aiKeyword)
                        .or()
                        .like("features", aiKeyword)
                );
            }

            // 为了防止匹配到太多导致 Token 爆炸，最多捞取 10 个最新上架的
            productQuery.orderByDesc("create_time").last("LIMIT 10");
            List<ProductInfo> products = productMapper.selectList(productQuery);

            // 兜底逻辑：如果 AI 没识别出来，或者数据库里没搜到这个词，就给 5 个默认热销爆款
            if (products.isEmpty()) {
                log.info("⚠️ 未匹配到特定商品，使用默认热销商品推荐");
                products = productMapper.selectList(
                        new QueryWrapper<ProductInfo>().eq("status", 1).orderByDesc("create_time").last("LIMIT 5")
                );
            }

//            // 2. 原RAG ：查出前 5 个热销/上架商品
//            // 真实场景可以用 Like 模糊搜索客户提到的关键词，这里简化为由 AI 从 TOP 5 里挑
//            List<ProductInfo> products = productMapper.selectList(
//                    new QueryWrapper<ProductInfo>()
//                            .eq("status", 1) // 必须是上架的
//                            .last("LIMIT 5") // 防止 Token 爆炸，只给 AI 看前5个
//            );


            // 把商品列表转成字符串
            String productContext = products.stream()
                    .map(p -> String.format("- %s (¥%s): %s", p.getProductName(), p.getPrice(), p.getFeatures()))
                    .collect(Collectors.joining("\n"));

            // 3. 构建超级 Prompt（Generation 生成）
            StringBuilder sb = new StringBuilder();
            sb.append("你是一位金牌电商客服。请根据已知信息生成回复草稿。\n\n");

            // --- 知识库注入 (RAG) ---
            sb.append("【🔴 店铺现货清单 (请基于此推荐，严禁编造)】：\n");
            if (productContext.isEmpty()) {
                sb.append("(暂无商品数据)\n");
            } else {
                sb.append(productContext).append("\n");
            }
            sb.append("----------------\n");

            // --- 客户画像 ---
            sb.append(String.format("【客户画像】：姓名-%s，性别-%s，等级-%s\n", name, gender, level));
            if (level.contains("重要") || (rfm!=null && rfm.getMScore() >= 4)) {
                sb.append("⚠️ 此为高价值VIP，回复需极度尊贵，可适当暗示有优惠权限。\n");
            }else {
                sb.append("这是普通客户，回复要专业、高效、解决问题。\n");
            }

            // --- 用户消息 ---
            sb.append(String.format("【客户消息】：%s\n", interaction.getContent()));

            // --- 任务要求 ---
            sb.append("\n【输出要求】：\n");
            sb.append("1. 格式：情绪分析 ||| 回复话术\n");
            sb.append("2. 限制：回复话术必须是纯文本，禁止Markdown符号，口语化。\n");
            sb.append("3. 策略：如果客户咨询推荐，请务必从【店铺现货清单】中选择最合适的一款进行介绍，并结合其卖点。\n");

            // 3. 调用 AI
            String result = callDeepSeekApi(sb.toString());

            // 4. 存入数据库
            if (result != null) {
                interaction.setAiSuggestedReply(result);
                // 更新这一条记录
                interactionMapper.updateById(interaction);
            }

        } catch (Exception e) {
            log.error("生成回复失败", e);
        }
    }



    /**
     * 触发 AI 分析任务 (生成"价值诊断报告")
     */
    @Async("taskExecutor") // 告诉 Spring，这个方法要在后台线程池跑
    public void generateAiAdvice(Long targetCustId) {
        QueryWrapper<CustRfmSnapshot> queryWrapper = new QueryWrapper<>();
        if (targetCustId != null) {
            queryWrapper.eq("cust_id", targetCustId);
        }

        List<CustRfmSnapshot> snapshots = snapshotMapper.selectList(queryWrapper);
        log.info("开始生成客户价值诊断，共有 {} 位客户...", snapshots.size());

        for (CustRfmSnapshot customer : snapshots) {
            try {
                // 1. 准备基础数据
                CustomerInfo basicInfo = customerInfoMapper.selectById(customer.getCustId());
                String name = (basicInfo != null) ? basicInfo.getCustName() : "客户";

                // 2. 查最近一次交互 (作为辅助参考，但不是核心)
                CustInteraction lastInteraction = interactionMapper.selectOne(
                        new QueryWrapper<CustInteraction>()
                                .eq("cust_id", customer.getCustId())
                                .orderByDesc("create_time")
                                .last("LIMIT 1")
                );

                // 3. 🔥 构建"军师" Prompt 🔥
                StringBuilder promptBuilder = new StringBuilder();

                // 设定角色：你是给老板写报告的分析师，不是客服
                promptBuilder.append("你是一位资深的CRM数据分析师。请根据以下数据，为管理员生成一份【客户价值诊断简报】。\n");
                promptBuilder.append("你的读者是内部工作人员，请保持客观、专业、简练的语气。\n\n");

                // 核心数据：RFM
                promptBuilder.append(String.format(
                        "【客户档案】：\n" +
                                "- 姓名：%s (ID:%d)\n" +
                                "- RFM评分：R(近度)%d分, F(频度)%d分, M(额度)%d分\n" +
                                "- 当前系统判定等级：%s\n",
                        name, customer.getCustId(), customer.getRScore(), customer.getFScore(), customer.getMScore(), customer.getCustomerLevel()
                ));

                // 辅助数据：最近有没有闹事？
                if (lastInteraction != null) {
                    promptBuilder.append(String.format(
                            "- 近期动态：最近有一条“%s”记录，内容为：“%s”\n",
                            lastInteraction.getInteractionType(), lastInteraction.getContent()
                    ));
                }

                // 任务指令
                promptBuilder.append("\n【任务要求】：\n");
                promptBuilder.append("请必须分三个段落输出你的分析，段落之间用空行隔开。每段开头请严格使用以下指定的前缀：\n");
                promptBuilder.append("【状态判断】：(分析该客户是活跃、沉睡还是有流失风险)\n");
                promptBuilder.append("【价值评估】：(基于RFM分数分析其消费潜力)\n");
                promptBuilder.append("【决策建议】：(管理员下一步的具体操作，如发券激活、电话安抚等)\n");
                promptBuilder.append("格式底线：必须是纯文本！严禁使用任何加粗(**)、标题(#)、列表(-)等Markdown符号，只允许使用正常换行。\n");

                promptBuilder.append("\n注意：不要生成发给客户的短信！是给管理员看的建议！");

                String finalPrompt = promptBuilder.toString();

                // 4. 调用 AI
                String advice = callDeepSeekApi(finalPrompt);

                // 5. 保存结果
                if (advice != null) {
                    adviceMapper.delete(new QueryWrapper<AiAdviceHistory>().eq("cust_id", customer.getCustId()));

                    AiAdviceHistory history = new AiAdviceHistory();
                    history.setCustId(customer.getCustId());
                    history.setAdviceContent(advice);
                    history.setPromptUsed(finalPrompt);
                    history.setCreateTime(LocalDateTime.now());

                    adviceMapper.insert(history);
                    log.info("客户 {} 诊断完成", customer.getCustId());
                }

                Thread.sleep(300); // 防限流

            } catch (Exception e) {
                log.error("客户 {} 分析异常", customer.getCustId(), e);
            }
        }
    }


    /**
     * 🔥 新功能：生成驾驶舱每日经营简报
     * 搜集：今日销售额、订单数、待处理工单、高风险流失客户
     */
    /**
     * 🔥 修改后的生成日报方法：带缓存功能 + 完整逻辑
     */
    public String generateDailyInsight() {
        // 1. 检查缓存：如果是同一天，且缓存里有东西，直接返回缓存！
        if (LocalDate.now().equals(cacheDate) && dailyInsightCache != null) {
            log.info("命中缓存，直接返回今日日报");
            return dailyInsightCache;
        }

        try {
            // 2. 搜集今日数据
            // A. 今日订单 & 销售额
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            List<OrderInfo> todayOrders = orderMapper.selectList(
                    new QueryWrapper<OrderInfo>().ge("order_date", startOfDay)
            );
            double totalSales = todayOrders.stream().mapToDouble(o -> o.getOrderAmount().doubleValue()).sum();
            int orderCount = todayOrders.size();

            // B. 待处理工单数
            Long pendingCount = interactionMapper.selectCount(
                    new QueryWrapper<CustInteraction>().eq("status", 0)
            );

            // C. 高价值流失风险客户 (R分数低，M分数高)
            Long churnRiskCount = snapshotMapper.selectCount(
                    new QueryWrapper<CustRfmSnapshot>().le("r_score", 2).ge("m_score", 4)
            );

            // 3. 构建 Prompt (这也是被找回的部分)
            StringBuilder sb = new StringBuilder(); // ✅ 这行就是之前缺少的定义
            sb.append("你是一位电商运营总监。请根据今日数据生成一份【每日经营简报】。\n");
            sb.append(String.format("【今日数据】：销售额¥%.2f，订单数%d单。\n", totalSales, orderCount));
            sb.append(String.format("【风险预警】：当前积压待处理工单 %d 条；监测到 %d 位高价值客户存在流失风险。\n", pendingCount, churnRiskCount));

            sb.append("\n【要求】：\n");
            sb.append("1. 字数控制在80字以内，言简意赅。\n");
            sb.append("2. 语气要专业、像领导汇报。\n");
            sb.append("3. 如果有流失风险或积压工单，请给出具体的行动指令（如“请立即安排回访”）。\n");
            sb.append("4. 纯文本格式，不要Markdown。");

            // 4. 调用 AI
            String insight = callDeepSeekApi(sb.toString()); // 现在 sb 找到了，这行就不会红了

            if (insight != null) {
                // ✅ 成功后，更新缓存
                this.dailyInsightCache = insight;
                this.cacheDate = LocalDate.now();
                return insight;
            } else {
                return "AI 数据分析中，请稍后再试...";
            }

        } catch (Exception e) {
            log.error("生成日报失败", e);
            return "系统繁忙，无法生成简报。";
        }
    }


    //调用API
    private String callDeepSeekApi(String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", userPrompt);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[]{message});
        requestBody.put("temperature", 0.5); // 稍微调低温度，让分析更理性

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
            Map body = response.getBody();
            if (body != null && body.containsKey("choices")) {
                List choices = (List) body.get("choices");
                Map firstChoice = (Map) choices.get(0);
                Map msg = (Map) firstChoice.get("message");
                return (String) msg.get("content");
            }
        } catch (Exception e) {
            log.error("API调用失败: {}", e.getMessage());
        }
        return null;
    }





}