package com.candyd.customeraibiz.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/**
 * RFM评分规则实体类 - 对应数据库中的 rfm_rule 表
 */
@Data
@TableName("rfm_rule")
public class RfmRule {
    @TableId
    private Integer ruleId;     // 规则ID
    private String itemType;    // 规则类型: R, F, M
    private BigDecimal minValue; // 该档位最小值
    private BigDecimal maxValue; // 该档位最大值
    private Integer score;       // 对应分数(1-5)
}