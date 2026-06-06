package com.candyd.customeraibiz.vo; // <--- 改成这里！

import lombok.Data;
import java.time.LocalDate;

/**
 * 客户列表展示对象 (View Object)
 */
@Data
public class CustomerListVO {
    // --- 基础信息 ---
    private Long custId;
    private String custName;
    private String gender;
    private String phone;
    private LocalDate birthday;

    // --- 当前客户策略 ---
    private String valueTier;
    private String lifecycleRisk;

    // --- AI 状态 ---
    private String latestAdvice;
    private Boolean hasAdvice;
}
