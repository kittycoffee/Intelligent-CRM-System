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

    // 展示用临时字段，不写入 ai_advice_history 表。
    @TableField(exist = false)
    private String valueTier;

    @TableField(exist = false)
    private String lifecycleRisk;
}
