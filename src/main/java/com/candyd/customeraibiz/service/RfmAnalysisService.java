package com.candyd.customeraibiz.service;

/**
 * RFM分析服务接口 - 定义了系统要做哪些高层业务逻辑
 */
public interface RfmAnalysisService {
    /**
     * 执行全量RFM分析计算
     * 逻辑：提取订单 -> 聚合计算 -> 匹配规则 -> 保存快照
     */
    void executeFullAnalysis();

    void analyzeCustomer(Long custId);   // 新增：单独分析某个客户
}