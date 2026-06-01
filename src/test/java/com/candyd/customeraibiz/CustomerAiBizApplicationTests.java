package com.candyd.customeraibiz;

import com.candyd.customeraibiz.service.RfmAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CustomerAiBizApplicationTests {

    @Test
    void contextLoads() {
    }

    @Autowired
    private RfmAnalysisService rfmAnalysisService;

    @Test
    @Disabled("Manual integration test: writes RFM snapshots to the local database")
    void testAnalysis() {
        rfmAnalysisService.executeFullAnalysis(); // 运行你刚才写的逻辑
    }

    // 在 CustomerAiBizApplicationTests 类里添加

    @Autowired
    private com.candyd.customeraibiz.service.AiMarketingService aiMarketingService;

    @Test
    @Disabled("Manual integration test: invokes the LLM and writes advice history")
    void testAiGeneration() {
        System.out.println("=== AI 营销大脑启动 ===");
        aiMarketingService.generateAiAdvice(null);
        System.out.println("=== 分析结束，请查看数据库 ai_advice_history 表 ===");
    }
}
