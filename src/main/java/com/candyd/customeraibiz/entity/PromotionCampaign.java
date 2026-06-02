package com.candyd.customeraibiz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("promotion_campaign")
public class PromotionCampaign {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String campaignName;
    private String campaignType;
    private String description;
    private String allowedReplyText;
    private String targetValueTier;
    private String targetLifecycleRisk;
    private String productCategory;
    private BigDecimal minOrderAmount;
    private BigDecimal discountAmount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
}

