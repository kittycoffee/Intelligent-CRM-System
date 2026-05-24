package com.candyd.customeraibiz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI 建议历史记录实体类
 * 修正版：严格对应数据库字段
 */
@Data
@TableName("ai_advice_history")
public class AiAdviceHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long custId;

    // --- 修正点 1：映射数据库的 advice_text 字段 ---
    @TableField("advice_text")
    private String adviceContent;

    // --- 修正点 2：数据库里有 prompt_used 字段，我们把它加上 ---
    private String promptUsed;

    private LocalDateTime createTime;

    // --- 修正点 3：数据库表里没有 customer_level 字段 ---
    // 必须加 exist = false，告诉 MyBatis-Plus 插入时忽略这个字段，否则会报 "Unknown column" 错误
    @TableField(exist = false)
    private String customerLevel;
}