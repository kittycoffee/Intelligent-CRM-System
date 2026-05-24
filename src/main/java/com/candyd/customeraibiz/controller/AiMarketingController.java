package com.candyd.customeraibiz.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.AiAdviceHistory;
import com.candyd.customeraibiz.entity.CustRfmSnapshot;
import com.candyd.customeraibiz.mapper.AiAdviceHistoryMapper;
import com.candyd.customeraibiz.mapper.CustRfmSnapshotMapper;
import com.candyd.customeraibiz.service.AiMarketingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 营销大脑的控制中心
 */
//前后端交互：自动将 Java 对象序列化为 JSON 字符串返回给前端
@RestController
@RequestMapping("/ai") // 这一类请求都以 /ai 开头
@CrossOrigin(origins = "*") // <--- 给前端发通行证
public class AiMarketingController {

    @Autowired
    private AiMarketingService aiMarketingService;

    @Autowired
    private AiAdviceHistoryMapper adviceMapper;

    // --- 新增：注入 RFM 快照的 Mapper，用来查等级 ---
    @Autowired
    private CustRfmSnapshotMapper snapshotMapper;

    /**
     * 触发 AI 生成建议
     * 浏览器访问：http://localhost:8080/ai/generate
     */

    // 接收前端 GET 请求，自动绑定查询参数
    @GetMapping("/generate")
// @RequestParam(required = false) 表示这个参数可传可不传
// 传了就是指定某人，不传就是 null (代表全量分析)
    public String triggerAiGeneration(@RequestParam(required = false) Long custId) {
        // 把 ID 传给 Service
        aiMarketingService.generateAiAdvice(custId);
        return "AI 营销建议生成任务已启动！请稍等几秒后访问 /ai/history 查看结果。";
    }

    /**
     * 查看 AI 的建议历史
     * 浏览器访问：http://localhost:8080/ai/history
     */
    @GetMapping("/history")
    public List<AiAdviceHistory> getAiHistory() {
        // 1. 先查出所有的建议记录 (按时间倒序)
        List<AiAdviceHistory> historyList = adviceMapper.selectList(
                new QueryWrapper<AiAdviceHistory>().orderByDesc("create_time")
        );

        // 2. 遍历每一条记录，去查对应的客户等级，然后填进去
        // (这是典型的 "内存组装" 方式，虽然不是性能最高的，但逻辑最清晰)
        for (AiAdviceHistory history : historyList) {
            Long custId = history.getCustId();

            // 去 snapshot 表里查这个客户最新的等级
            // 注意：因为一个客户可能有多个日期的快照，我们取日期最新的那一条
            CustRfmSnapshot snapshot = snapshotMapper.selectOne(
                    new QueryWrapper<CustRfmSnapshot>()
                            .eq("cust_id", custId)
                            .orderByDesc("snapshot_date") // 按日期倒序
                            .last("LIMIT 1") // 只取第一条
            );

            if (snapshot != null) {
                // 查到了！把等级塞进 history 对象里的临时字段
                history.setCustomerLevel(snapshot.getCustomerLevel());
            } else {
                // 没查到 (可能是新客户还没做RFM分析)
                history.setCustomerLevel("未知等级");
            }
        }

        // 3. 返回组装好的列表
        return historyList;
    }
    /**
     * 清空历史记录
     * 对应前端的 axios.delete 请求
     */
    // 解决“数据滞后（脏读）”的问题
    @DeleteMapping("/history")
    public String clearHistory() {
        // null 表示没有条件，全删！
        adviceMapper.delete(null);
        return "历史记录已清空！";
    }
}