package com.candyd.customeraibiz.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表展示对象 (View Object)
 * 作用：把数据库里的原始数据，包装成前端表格最好用的格式
 */
@Data
public class OrderListVO {
    // 1. 订单基础信息
    private String orderId;
    private Long custId;
    private String custName;      // 💡 重点：这是 Entity 里没有的，要额外查出来

    // 2. 消费详情 (这三个是你刚加的)
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;

    // 3. 统计与状态
    private BigDecimal orderAmount; // 总金额
    private LocalDateTime orderDate;
    private Integer orderStatus;
}