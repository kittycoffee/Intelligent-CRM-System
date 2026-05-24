package com.candyd.customeraibiz.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.CustInteraction;
import com.candyd.customeraibiz.entity.CustomerInfo;
import com.candyd.customeraibiz.mapper.CustInteractionMapper;
import com.candyd.customeraibiz.mapper.CustomerInfoMapper;
import com.candyd.customeraibiz.service.AiMarketingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/interaction")
@CrossOrigin(origins = "*")
public class InteractionController {

    @Autowired private CustInteractionMapper interactionMapper;
    @Autowired private CustomerInfoMapper customerMapper;
    @Autowired private AiMarketingService aiService;

    // 1. 获取待办列表 (保持不变)
    @GetMapping("/pending")
    public List<Map<String, Object>> getPending() {
        QueryWrapper<CustInteraction> qw = new QueryWrapper<>();
        qw.eq("status", 0).orderByDesc("create_time");
        List<CustInteraction> list = interactionMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CustInteraction i : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", i.getId());
            map.put("custId", i.getCustId());
            map.put("custName", customerMapper.selectById(i.getCustId()).getCustName());
            map.put("type", i.getInteractionType());
            map.put("content", i.getContent());
            map.put("createTime", i.getCreateTime());
            // 把 AI 算好的回复给前端 (可能为空)
            map.put("aiSuggestedReply", i.getAiSuggestedReply());
            result.add(map);
        }
        return result;
    }

    // 2. 录入接口 (修改版：只存数据，快速返回)
    @PostMapping("/add")
    public String addInteraction(@RequestBody Map<String, Object> params) {
        Long custId = Long.valueOf(params.get("custId").toString());
        String content = (String) params.get("content");
        String type = params.containsKey("type") ? (String) params.get("type") : "日常沟通";

        CustInteraction interaction = new CustInteraction();
        interaction.setCustId(custId);
        interaction.setContent(content);
        interaction.setInteractionType(type);
        interaction.setCreateTime(LocalDateTime.now());
        interaction.setStatus(0);

        interactionMapper.insert(interaction);

        // 🔥 修改点：异步处理防止系统阻塞 (后端)
        // 去掉 generateReplyDraft 的同步调用，避免卡死
        //new Thread很危险，如果线程突然加了很多，会导致服务器内存直接被撑爆；用 Spring Boot 的 `@Async` 注解
        //new Thread(() -> aiService.generateAiAdvice(custId)).start();

        // 因为你在 Service 加上了 @Async，这里现在直接调就行了，Spring 会自动把它扔进线程池异步执行！
        aiService.generateAiAdvice(custId);

        return "录入成功";
    }

    // 3. ✅ 新增接口：手动触发工单的 AI 回复生成
    // 用于工单处理界面，点击“生成建议”时调用
    @GetMapping("/analyze/{id}")
    public String analyzeInteraction(@PathVariable Long id) {
        CustInteraction interaction = interactionMapper.selectById(id);
        if (interaction == null) return "记录不存在";

        // 调用 AI 生成 (这是一个耗时操作)
        aiService.generateReplyDraft(interaction);

        // 重新查出来返回给前端
        return interactionMapper.selectById(id).getAiSuggestedReply();
    }

    // 4. 处理完成接口 (保持不变)
    @PostMapping("/handle")
    public String handle(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        String result = (String) params.get("handleResult");
        CustInteraction i = new CustInteraction();
        i.setId(id); i.setStatus(1); i.setHandleResult(result);
        interactionMapper.updateById(i);
        return "success";
    }

    // 5. 历史记录接口 (保持不变)
    @GetMapping("/history")
    public List<Map<String, Object>> getHistory() {
        QueryWrapper<CustInteraction> qw = new QueryWrapper<>();
        qw.eq("status", 1).orderByDesc("create_time").last("LIMIT 100");
        List<CustInteraction> list = interactionMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CustInteraction i : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", i.getId());
            map.put("custId", i.getCustId());
            map.put("custName", customerMapper.selectById(i.getCustId()).getCustName());
            map.put("type", i.getInteractionType());
            map.put("content", i.getContent());
            map.put("createTime", i.getCreateTime());
            map.put("handleResult", i.getHandleResult());
            result.add(map);
        }
        return result;
    }

    /**
     * 重新生成 AI 回复话术
     */
    /**
     * 重新生成 AI 回复话术
     */
    @PostMapping("/regenerate-reply/{id}")
    public Map<String, Object> regenerateReply(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 使用 Mapper 查询工单记录 (修复点 1)
            CustInteraction interaction = interactionMapper.selectById(id);
            if (interaction == null) {
                result.put("code", 404);
                result.put("msg", "未找到该交互记录");
                return result;
            }

            // 2. 使用注入的小写实例对象 aiService 调用方法 (修复点 2)
            aiService.generateReplyDraft(interaction);

            // 3. 再次从数据库查出最新的记录返回 (修复点 1)
            CustInteraction updatedInteraction = interactionMapper.selectById(id);

            result.put("code", 200);
            result.put("msg", "话术已更新");
            result.put("data", updatedInteraction.getAiSuggestedReply());
            return result;

        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "生成失败：" + e.getMessage());
            return result;
        }
    }
}