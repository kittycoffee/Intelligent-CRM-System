package com.candyd.customeraibiz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("cust_rfm_snapshot")
public class CustRfmSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long custId;
    private Integer rScore; // 已报废：仅兼容历史快照，不再写入或展示
    private Integer fScore; // 已报废：仅兼容历史快照，不再写入或展示
    private Integer mScore; // 已报废：仅兼容历史快照，不再写入或展示
    private String customerLevel; // 已报废：旧版混合等级，仅兼容历史数据
    private String valueTier; // 当前策略：high / normal
    private String lifecycleRisk; // 当前策略：active / silent / churn_risk
    private LocalDate snapshotDate; // 快照日期
}
