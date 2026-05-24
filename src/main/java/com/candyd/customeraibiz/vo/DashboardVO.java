package com.candyd.customeraibiz.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List; // 记得导入 List

/**
 * 首页仪表盘数据对象
 */
@Data
public class DashboardVO {
    private Integer totalCustomers;    // 客户总数
    private BigDecimal totalRevenue;   // 总营收
    private Integer totalOrders;       // 订单总数
    private Integer totalInteractions; // 交互记录总数

    // --- 👇 新增：给折线图用的数据 👇 ---
    private List<String> chartDates;      // X轴：最近7天的日期
    private List<BigDecimal> chartValues; // Y轴：每天的销售额
}