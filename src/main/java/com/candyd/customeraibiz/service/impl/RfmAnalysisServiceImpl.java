package com.candyd.customeraibiz.service.impl;

import com.candyd.customeraibiz.entity.CustRfmSnapshot;
import com.candyd.customeraibiz.entity.OrderInfo;
import com.candyd.customeraibiz.entity.RfmRule;
import com.candyd.customeraibiz.mapper.CustRfmSnapshotMapper;
import com.candyd.customeraibiz.mapper.OrderInfoMapper;
import com.candyd.customeraibiz.mapper.RfmRuleMapper;
import com.candyd.customeraibiz.service.RfmAnalysisService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RFM分析服务的具体实现 - 也就是系统的“大脑”
 */
@Slf4j
@Service
public class RfmAnalysisServiceImpl implements RfmAnalysisService {

    @Autowired
    private CustRfmSnapshotMapper snapshotMapper;

    @Autowired
    private OrderInfoMapper orderInfoMapper; // 注入订单搬运工

    @Autowired
    private RfmRuleMapper rfmRuleMapper; // 注入规则搬运工

    @Override
    // 计算逻辑
    public void executeFullAnalysis() {
        log.info("开始执行全量客户价值分析...");

        // 1. 获取所有订单和所有规则
        List<OrderInfo> allOrders = orderInfoMapper.selectList(null);
        List<RfmRule> allRules = rfmRuleMapper.selectList(null); // 把说明书全拿出来

        // 2. 按客户分组计算原始值
        Map<Long, List<OrderInfo>> customerGroup = allOrders.stream()
                .collect(Collectors.groupingBy(OrderInfo::getCustId));

        customerGroup.forEach((custId, orders) -> {
            // --- 之前的计算原始值逻辑 ---
            int fValue = orders.size();
            BigDecimal mValue = orders.stream()
                    .map(OrderInfo::getOrderAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            LocalDateTime lastDate = orders.stream()
                    .map(OrderInfo::getOrderDate)
                    .max(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now()); // 增加默认值预防崩溃
            long rValue = ChronoUnit.DAYS.between(lastDate, LocalDateTime.now());

            // 3. 核心打分逻辑：对照规则表
            int rScore = calculateScore("R", new BigDecimal(rValue), allRules);
            int fScore = calculateScore("F", new BigDecimal(fValue), allRules);
            int mScore = calculateScore("M", mValue, allRules);

            log.info("客户 ID: {}, R得分: {}, F得分: {}, M得分: {}", custId, rScore, fScore, mScore);
            // ...之前的打分逻辑...

            // 4. 判定客户等级 (简单逻辑：均分法)
            // 假设平均分大于 3 分就是重要客户，否则是一般客户
            //            String level = (rScore + fScore + mScore) / 3.0 >= 3 ? "重要价值客户" : "一般保持客户";

            // 算出平均分
            double avgScore = (rScore + fScore + mScore) / 3.0;

            // 4. 判定客户等级 ———— 制定三档分群规则
            String level;
            if (avgScore >= 3.5) {
                level = "重要价值客户";   // 均分 3.5 以上，绝对的优质客户
            } else if (avgScore >= 2.5) {
                level = "一般保持客户";   // 均分 2.5 ~ 3.4 之间，需要促活
            } else {
                level = "流失风险客户";   // 均分低于 2.5，边缘客户
            }

            // 5. 封装快照对象并保存
            CustRfmSnapshot snapshot = new CustRfmSnapshot();
            snapshot.setCustId(custId);
            snapshot.setRScore(rScore);
            snapshot.setFScore(fScore);
            snapshot.setMScore(mScore);
            snapshot.setCustomerLevel(level);
            snapshot.setSnapshotDate(LocalDate.now());

            snapshotMapper.insert(snapshot); // 执行保存
            log.info("客户 {} 的分析结果已保存", custId);
        });
    }

//    新增但客户分析函数
@Override
public void analyzeCustomer(Long custId) {
    // 获取该客户所有订单
    List<OrderInfo> orders = orderInfoMapper.selectList(
            new QueryWrapper<OrderInfo>().eq("cust_id", custId)
    );
    if (orders.isEmpty()) return;

    // 获取所有规则
    List<RfmRule> allRules = rfmRuleMapper.selectList(null);

    // 计算原始值
    // 1. 算 F值 (频度 Frequency)
    int fValue = orders.size();
    // 解释：最简单粗暴，这个客户有几个订单，F 的原始值就是几。

    // 2. 算 M值 (额度 Monetary)
    BigDecimal mValue = orders.stream()
            .map(OrderInfo::getOrderAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    // 解释：计算总消费金额。
    // 把订单列表变成流，用 map 提取出每个订单的金额 (OrderAmount)，然后用 reduce 把它们从 0 开始累加起来。
    // 这里坚持使用 BigDecimal 进行累加，避免了浮点数（double/float）运算带来的精度丢失。

    // 3. 算 R值 (近度 Recency)
    //这里是“流式计算”
    LocalDateTime lastDate = orders.stream()
            .map(OrderInfo::getOrderDate)
            .max(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());
    long rValue = ChronoUnit.DAYS.between(lastDate, LocalDateTime.now());
    // 解释：计算距离上次购买的天数。
    // 同样提取出所有订单的下单时间，用 max 找出最近（最大）的一个时间。
    // orElse(LocalDateTime.now()) 是极佳的防御性编程：万一出了鬼畜 Bug 导致订单列表为空，默认他今天刚买过，防止抛出 NullPointerException。
    // 最后算天数差作为 R 原始值。

    // 打分（把R,F,M进行规则匹配，转换成 1-5 的标准化评分。)
    int rScore = calculateScore("R", new BigDecimal(rValue), allRules);
    int fScore = calculateScore("F", new BigDecimal(fValue), allRules);
    int mScore = calculateScore("M", mValue, allRules);

//    // 判定等级
//    String level = (rScore + fScore + mScore) / 3.0 >= 3 ? "重要价值客户" : "一般保持客户";

    // 算出平均分
    double avgScore = (rScore + fScore + mScore) / 3.0;

    // 制定三档分群规则
    String level;
    if (avgScore >= 3.5) {
        level = "重要价值客户";   // 均分 3.5 以上，绝对的优质客户
    } else if (avgScore >= 2.5) {
        level = "一般保持客户";   // 均分 2.5 ~ 3.4 之间，需要促活
    } else {
        level = "流失风险客户";   // 均分低于 2.5，边缘客户
    }

    // 删除该客户的旧快照，避免累积多条
    snapshotMapper.delete(new QueryWrapper<CustRfmSnapshot>().eq("cust_id", custId));

    // 保存新快照
    CustRfmSnapshot snapshot = new CustRfmSnapshot();
    snapshot.setCustId(custId);
    snapshot.setRScore(rScore);
    snapshot.setFScore(fScore);
    snapshot.setMScore(mScore);
    snapshot.setCustomerLevel(level);
    snapshot.setSnapshotDate(LocalDate.now());
    snapshotMapper.insert(snapshot);
}


    /**
     * 这是一个内部辅助方法：根据数值和规则表算出得分
     */
    private int calculateScore(String type, BigDecimal value, List<RfmRule> rules) {
        return rules.stream()
                .filter(rule -> rule.getItemType().equals(type)) // 只看对应类型的规则
                .filter(rule -> value.compareTo(rule.getMinValue()) >= 0 && value.compareTo(rule.getMaxValue()) < 0) // 判断落在哪个区间
                .map(RfmRule::getScore)
                .findFirst()
                .orElse(1); // 如果没匹配到，默认给 1 分（兜底逻辑）
    }
}