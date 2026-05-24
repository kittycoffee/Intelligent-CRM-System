package com.candyd.customeraibiz;

import com.candyd.customeraibiz.service.RfmAnalysisService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

@SpringBootTest
class CustomerAiBizApplicationTests {

    @Test
    void contextLoads() {
    }

    @Autowired
    private RfmAnalysisService rfmAnalysisService;

    @Test
    void testAnalysis() {
        rfmAnalysisService.executeFullAnalysis(); // 运行你刚才写的逻辑
    }

    // 在 CustomerAiBizApplicationTests 类里添加

    @Autowired
    private com.candyd.customeraibiz.service.AiMarketingService aiMarketingService;

    @Test
    @Rollback(false) // 强制要求：不许回滚，把数据真的留下来！
    void testAiGeneration() {
        System.out.println("=== AI 营销大脑启动 ===");
        aiMarketingService.generateAiAdvice(null);
        System.out.println("=== 分析结束，请查看数据库 ai_advice_history 表 ===");
    }
}
