package com.candyd.customeraibiz.controller;

import com.candyd.customeraibiz.entity.CustRfmSnapshot;
import com.candyd.customeraibiz.mapper.CustRfmSnapshotMapper;
import com.candyd.customeraibiz.service.RfmAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RFM 业务的控制中心
 */
@RestController // 告诉 Spring Boot：我是服务员，我返回的是数据（JSON），不是网页 HTML
@RequestMapping("/rfm") // 这一类请求都以 /rfm 开头
public class RfmController {

    @Autowired
    private RfmAnalysisService rfmAnalysisService;

    @Autowired
    private CustRfmSnapshotMapper snapshotMapper;

    /**
     * 触发全量分析
     * 浏览器访问：http://localhost:8080/rfm/analyze
     */
    @GetMapping("/analyze")
    public String triggerAnalysis() {
        rfmAnalysisService.executeFullAnalysis();
        return "RFM 全量分析已完成！请访问 /rfm/list 查看结果。";
    }

    /**
     * 查看分析结果
     * 浏览器访问：http://localhost:8080/rfm/list
     */
    @GetMapping("/list")
    public List<CustRfmSnapshot> getAllSnapshots() {
        // 直接让 Mapper 去数据库查所有数据返回给浏览器
        return snapshotMapper.selectList(null);
    }
}