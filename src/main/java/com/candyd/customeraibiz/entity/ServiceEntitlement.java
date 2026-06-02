package com.candyd.customeraibiz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("service_entitlement")
public class ServiceEntitlement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String entitlementName;
    private String description;
    private String allowedReplyText;
    private String applicableValueTier;
    private String applicableIntent;
    private String responseSla;
    private String priorityLevel;
    private Integer requiresManualApproval;
    private String maxPromiseLevel;
    private Integer status;
}

