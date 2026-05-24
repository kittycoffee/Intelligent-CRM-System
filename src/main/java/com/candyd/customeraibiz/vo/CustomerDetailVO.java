package com.candyd.customeraibiz.vo;

import com.candyd.customeraibiz.entity.AiAdviceHistory;
import com.candyd.customeraibiz.entity.CustInteraction;
import com.candyd.customeraibiz.entity.CustRfmSnapshot;
import com.candyd.customeraibiz.entity.OrderInfo;
import com.candyd.customeraibiz.entity.CustomerInfo;
import lombok.Data;
import java.util.List;

/**
 * 客户详情展示对象 (全家桶)
 * 专门用于详情页，包含了列表页没有的 "List" 详细数据
 */
@Data
public class CustomerDetailVO {

    // 1. 基础信息
    private CustomerInfo basicInfo;

    // 2. RFM 画像
    private CustRfmSnapshot rfmSnapshot;

    // 3. AI 建议 (最新的一条)
    private AiAdviceHistory latestAdvice;

    // 4. 历史订单列表 (这是 ListVO 里没有的)
    private List<OrderInfo> orderList;

    // 5. 交互记录列表 (这是 ListVO 里没有的)
    private List<CustInteraction> interactionList;
}