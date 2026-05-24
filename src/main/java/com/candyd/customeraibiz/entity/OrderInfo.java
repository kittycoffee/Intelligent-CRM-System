package com.candyd.customeraibiz.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类 - 对应数据库中的 order_info 表
 */
@Data
@TableName("order_info")
public class OrderInfo {
    @TableId
    private String orderId;      // 订单编号
    private Long custId;         // 关联客户ID
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal orderAmount; // 订单金额
    private LocalDateTime orderDate; // 下单时间
    private Integer orderStatus;  // 订单状态
}