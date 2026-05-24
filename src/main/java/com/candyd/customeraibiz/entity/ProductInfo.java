package com.candyd.customeraibiz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品信息表实体
 */
@Data
@TableName("product_info")
public class ProductInfo {

    @TableId(type = IdType.AUTO)
    private Integer productId;

    private String productName;

    private String category;

    private BigDecimal price;

    private Integer stock;

    /**
     * 核心字段：卖点/特征
     * 这个字段的内容会直接喂给 AI，作为推荐理由
     */
    private String features;

    private Integer status; // 1上架 0下架

    private LocalDateTime createTime;
}