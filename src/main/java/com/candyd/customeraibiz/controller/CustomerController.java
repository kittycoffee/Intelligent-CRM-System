package com.candyd.customeraibiz.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.*;
import com.candyd.customeraibiz.mapper.*;
import com.candyd.customeraibiz.service.AiMarketingService;
import com.candyd.customeraibiz.service.RfmAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/customer")
@CrossOrigin(origins = "*")
public class CustomerController {

    @Autowired private CustomerInfoMapper customerMapper;
    @Autowired private CustRfmSnapshotMapper rfmMapper;
    @Autowired private AiAdviceHistoryMapper adviceMapper;
    @Autowired private CustInteractionMapper interactionMapper; // ✅ 确保注入了这几个 Mapper
    @Autowired private OrderInfoMapper orderMapper;             // ✅ 确保注入了这几个 Mapper
    @Autowired private AiMarketingService aiService;
    @Autowired private RfmAnalysisService rfmAnalysisService;   //新导入 5-5

    /**
     * 1. 列表查询接口 (大礼包模式)
     */
    @GetMapping("/list")
    public List<Map<String, Object>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String gender
    ) {
        QueryWrapper<CustomerInfo> query = new QueryWrapper<>();
        if (StringUtils.hasText(name)) query.like("cust_name", name);
        if (StringUtils.hasText(gender)) query.eq("gender", gender);

        List<CustomerInfo> customers = customerMapper.selectList(query);
        List<Map<String, Object>> result = new ArrayList<>();

        for (CustomerInfo info : customers) {
            Map<String, Object> item = new HashMap<>();
            item.put("info", info);

            CustRfmSnapshot rfm = rfmMapper.selectOne(
                    new QueryWrapper<CustRfmSnapshot>().eq("cust_id", info.getCustId()).last("LIMIT 1")
            );
            item.put("rfm", rfm != null ? rfm : new CustRfmSnapshot());

            AiAdviceHistory advice = adviceMapper.selectOne(
                    new QueryWrapper<AiAdviceHistory>().eq("cust_id", info.getCustId()).orderByDesc("create_time").last("LIMIT 1")
            );
            // 列表页只需要看个大概，所以只返回字符串内容
            item.put("aiAdvice", advice != null ? advice.getAdviceContent() : null);

            if (StringUtils.hasText(level)) {
                String currentLevel = (rfm != null) ? rfm.getCustomerLevel() : "";
                if (!level.equals(currentLevel)) continue;
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 2. 🔥 客户全景档案（详情页数据聚合）
     * 这里必须要把 订单、交互、AI建议 全部查出来
     */
    @GetMapping("/detail")
    public Map<String, Object> getDetail(@RequestParam Long custId) {
        Map<String, Object> data = new HashMap<>();

        // A. 基础信息
        data.put("info", customerMapper.selectById(custId));

        // B. RFM 画像
        data.put("rfm", rfmMapper.selectOne(
                new QueryWrapper<CustRfmSnapshot>().eq("cust_id", custId).last("LIMIT 1")
        ));

        // C. AI 建议 (返回完整对象，以便前端能取到 adviceContent)
        data.put("aiAdvice", adviceMapper.selectOne(
                new QueryWrapper<AiAdviceHistory>()
                        .eq("cust_id", custId)
                        .orderByDesc("create_time")
                        .last("LIMIT 1")
        ));

        // D. 历史交互记录 (按时间倒序)
        data.put("interactions", interactionMapper.selectList(
                new QueryWrapper<CustInteraction>()
                        .eq("cust_id", custId)
                        .orderByDesc("create_time")
        ));

        // E. 消费订单 (最近 10 条)
        data.put("orders", orderMapper.selectList(
                new QueryWrapper<OrderInfo>()
                        .eq("cust_id", custId)
                        .orderByDesc("order_date")
                        .last("LIMIT 10")
        ));

        return data;
    }

    /**
     * 3. 手动触发诊断
     */
//    @PostMapping("/diagnose/{custId}")
//    public String manualDiagnose(@PathVariable Long custId) {
//        aiService.generateAiAdvice(custId);
//        return "诊断完成";
//    }

//    修改，之前的方法会导致ai诊断只会基于旧数据
    @PostMapping("/diagnose/{custId}")
    public String manualDiagnose(@PathVariable Long custId) {
        // 1. 先基于全部订单重新计算 RFM，刷新快照
        rfmAnalysisService.analyzeCustomer(custId);
        // 2. 再调用 AI 生成最新诊断
        aiService.generateAiAdvice(custId);
        return "诊断完成";
    }

}