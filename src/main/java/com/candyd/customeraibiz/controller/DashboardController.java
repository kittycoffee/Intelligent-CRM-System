package com.candyd.customeraibiz.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.*;
import com.candyd.customeraibiz.mapper.*;
import com.candyd.customeraibiz.service.AiMarketingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired private CustomerInfoMapper customerMapper;
    @Autowired private OrderInfoMapper orderMapper;
    @Autowired private CustInteractionMapper interactionMapper;
    @Autowired private AiMarketingService aiService;


    /**
     * 1. 获取统计数字和图表数据 (用于首页 4 个卡片 + 折线图)
     */
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> data = new HashMap<>();

        // 1. 核心卡片数据 (保持不变)
        data.put("totalCustomers", customerMapper.selectCount(null));
        data.put("totalInteractions", interactionMapper.selectCount(null));

        List<OrderInfo> allOrders = orderMapper.selectList(null);
        double totalRevenue = allOrders.stream().mapToDouble(o -> o.getOrderAmount().doubleValue()).sum();
        data.put("totalRevenue", (int) totalRevenue);
        data.put("totalOrders", allOrders.size());

        // ---------------------------------------------------------
        // 2. 折线图数据
        // ---------------------------------------------------------

        // A. 准备最近 7 天的日期列表 (作为 X 轴)
        List<String> dates = new ArrayList<>();
        LocalDate today = LocalDate.now();
        // 计算 6 天前的日期 (折线图起始日期)
        LocalDate startDate = today.minusDays(6);

        for (int i = 0; i < 7; i++) {
            dates.add(startDate.plusDays(i).format(DateTimeFormatter.ofPattern("MM-dd")));
        }
        data.put("chartDates", dates); // X 轴坐标

        // B. 去数据库查真实的销售数据
        // 查出来的数据可能是稀疏的 (比如只有 18号 和 20号 有数据)
        List<Map<String, Object>> dbData = orderMapper.selectLast7DaysSales(
                startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 00:00:00"
        );

        // 把数据库查出来的 List 转成 Map 方便查找，key 是日期 "01-18"，value 是金额
        Map<String, Double> salesMap = new HashMap<>();
        for (Map<String, Object> record : dbData) {
            String d = (String) record.get("dateStr");
            BigDecimal val = (BigDecimal) record.get("totalVal");
            salesMap.put(d, val.doubleValue());
        }

        // C. 组装 Y 轴数据 (如果某天没卖出东西，就填 0，而不是随机数)
        List<Double> values = new ArrayList<>();
        for (String date : dates) {
            // 如果这一天在数据库里有记录，就用记录值；否则填 0
            values.add(salesMap.getOrDefault(date, 0.0));
        }
        data.put("chartValues", values);

        return data;
    }

    /**
     * 2. 🔥 获取 AI 每日经营简报
     */
    @GetMapping("/insight")
    public String getAiInsight() {
        // 这里可以直接调用，因为是首页，且一天变动不大，
        // 进阶做法是可以存 Redis 缓存 1 小时，但为了毕设演示实时性，直接调也没问题
        return aiService.generateDailyInsight();
    }
}