package com.candyd.customeraibiz.service;

/**
 * 客户策略快照服务接口。
 * 类名暂时保留以减少迁移范围，当前逻辑已从旧 RFM 等级切换为价值等级与生命周期风险。
 */
public interface RfmAnalysisService {
    /**
     * 执行全量客户策略分析。
     * 逻辑：提取订单 -> 聚合计算 -> 分别计算价值等级和生命周期风险 -> 保存快照
     */
    void executeFullAnalysis();

    void analyzeCustomer(Long custId);   // 新增：单独分析某个客户
}
