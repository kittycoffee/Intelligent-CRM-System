package com.candyd.customeraibiz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.CustInteraction;
import com.candyd.customeraibiz.entity.CustRfmSnapshot;
import com.candyd.customeraibiz.entity.CustomerInfo;
import com.candyd.customeraibiz.entity.OrderInfo;
import com.candyd.customeraibiz.mapper.CustInteractionMapper;
import com.candyd.customeraibiz.mapper.CustRfmSnapshotMapper;
import com.candyd.customeraibiz.mapper.CustomerInfoMapper;
import com.candyd.customeraibiz.mapper.OrderInfoMapper;
import com.candyd.customeraibiz.service.RfmAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 客户策略快照服务。
 * 旧版 R/F/M 打分和 customerLevel 已报废；当前只维护价值等级与生命周期风险。
 */
@Slf4j
@Service
public class RfmAnalysisServiceImpl implements RfmAnalysisService {

    @Autowired
    private CustRfmSnapshotMapper snapshotMapper;

    @Autowired
    private CustomerInfoMapper customerInfoMapper;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private CustInteractionMapper interactionMapper;

    @Override
    public void executeFullAnalysis() {
        log.info("开始执行全量客户策略分析...");
        List<CustomerInfo> customers = customerInfoMapper.selectList(null);
        customers.forEach(customer -> analyzeCustomer(customer.getCustId()));
        log.info("全量客户策略分析完成，共刷新 {} 位客户", customers.size());
    }

    @Override
    public void analyzeCustomer(Long custId) {
        List<OrderInfo> orders = orderInfoMapper.selectList(
                new QueryWrapper<OrderInfo>()
                        .eq("cust_id", custId)
                        .eq("order_status", 1)
        );

        int orderCount = orders.size();
        BigDecimal totalAmount = orders.stream()
                .map(OrderInfo::getOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime lastOrderDate = orders.stream()
                .map(OrderInfo::getOrderDate)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        long daysSinceLastOrder = lastOrderDate == null
                ? 61
                : ChronoUnit.DAYS.between(lastOrderDate, LocalDateTime.now());

        // 每位客户只保留一条当前策略快照，重复执行全量分析也不会积累脏数据。
        snapshotMapper.delete(new QueryWrapper<CustRfmSnapshot>().eq("cust_id", custId));

        CustRfmSnapshot snapshot = new CustRfmSnapshot();
        snapshot.setCustId(custId);
        snapshot.setValueTier(calculateValueTier(orderCount, totalAmount));
        snapshot.setLifecycleRisk(calculateLifecycleRisk(custId, daysSinceLastOrder));
        snapshot.setSnapshotDate(LocalDate.now());
        snapshotMapper.insert(snapshot);
    }

    /**
     * 已报废：旧版 R/F/M 打分会把客户价值和流失风险混成一个 customerLevel。
     * 当前策略改为 calculateValueTier() 与 calculateLifecycleRisk() 两条独立规则。
     */
//    private int calculateScore(String type, BigDecimal value, List<RfmRule> rules) {
//        return rules.stream()
//                .filter(rule -> rule.getItemType().equals(type))
//                .filter(rule -> value.compareTo(rule.getMinValue()) >= 0 && value.compareTo(rule.getMaxValue()) < 0)
//                .map(RfmRule::getScore)
//                .findFirst()
//                .orElse(1);
//    }

    /**
     * 面试演示用的简化客户策略规则。阈值集中在这里，避免把它描述成完整商业模型。
     */
    private String calculateValueTier(int orderCount, BigDecimal totalAmount) {
        return orderCount >= 5 || totalAmount.compareTo(new BigDecimal("5000")) >= 0
                ? "high" : "normal";
    }

    private String calculateLifecycleRisk(Long custId, long daysSinceLastOrder) {
        long recentNegativeCount = countRecentNegativeInteractions(custId);
        if (daysSinceLastOrder > 60 && recentNegativeCount > 0) {
            return "churn_risk";
        }
        return daysSinceLastOrder > 30 ? "silent" : "active";
    }

    private long countRecentNegativeInteractions(Long custId) {
        LocalDateTime negativeWindowStart = LocalDateTime.now().minusDays(30);
        return interactionMapper.selectCount(
                new QueryWrapper<CustInteraction>()
                        .eq("cust_id", custId)
                        .ge("create_time", negativeWindowStart)
                        .and(wrapper -> wrapper
                                .eq("interaction_type", "投诉")
                                .or().like("content", "退款")
                                .or().like("content", "不满")
                                .or().like("content", "太差"))
        );
    }
}
