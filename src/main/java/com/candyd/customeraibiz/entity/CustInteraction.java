package com.candyd.customeraibiz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 客户交互记录表
 * 记录客户的投诉、咨询、回访等文本信息
 */
@Data // Lombok 注解：自动生成 get/set
@TableName("cust_interaction") // MP 注解：对应数据库表名
public class CustInteraction {

    @TableId(type = IdType.AUTO) // MP 注解：主键自增
    private Long id;

    private Long custId; // 关联哪个客户

    private String interactionType; // 类型：电话、邮件、投诉、咨询

    private String content; // 核心内容：比如“客户抱怨价格太贵”

    private LocalDateTime createTime; // 发生时间

    // --- 👇 新增这两个字段，报错就会消失 👇 ---
    private Integer status;         // 0=待处理, 1=已处理
    private String handleResult;    // 处理结果/人工回复

    private String aiSuggestedReply;
}