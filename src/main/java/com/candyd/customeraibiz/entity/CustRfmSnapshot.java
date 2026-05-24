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
    private Integer rScore;
    private Integer fScore;
    private Integer mScore;
    private String customerLevel; // 最终等级，如：重要价值客户
    private LocalDate snapshotDate; // 快照日期
}