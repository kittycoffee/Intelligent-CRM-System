package com.candyd.customeraibiz.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

/**
 * 客户基础信息表
 */
@Data
@TableName("customer_info")
public class CustomerInfo {

    @TableId // 主键
    private Long custId;

    private String custName;

    private String gender;

    private String phone;

    private LocalDate birthday;
}