package com.candyd.customeraibiz.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.CustInteraction;
import com.candyd.customeraibiz.entity.CustRfmSnapshot;
import com.candyd.customeraibiz.entity.CustomerInfo;
import com.candyd.customeraibiz.entity.OrderInfo;
import com.candyd.customeraibiz.entity.ProductInfo;
import com.candyd.customeraibiz.entity.PromotionCampaign;
import com.candyd.customeraibiz.entity.ServiceEntitlement;
import com.candyd.customeraibiz.mapper.CustInteractionMapper;
import com.candyd.customeraibiz.mapper.CustRfmSnapshotMapper;
import com.candyd.customeraibiz.mapper.CustomerInfoMapper;
import com.candyd.customeraibiz.mapper.OrderInfoMapper;
import com.candyd.customeraibiz.mapper.ProductInfoMapper;
import com.candyd.customeraibiz.mapper.PromotionCampaignMapper;
import com.candyd.customeraibiz.mapper.ServiceEntitlementMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiAgentWorkflowService {

    @Autowired private CustInteractionMapper interactionMapper;
    @Autowired private CustomerInfoMapper customerMapper;
    @Autowired private CustRfmSnapshotMapper rfmMapper;
    @Autowired private ProductInfoMapper productMapper;
    @Autowired private OrderInfoMapper orderMapper;
    @Autowired private PromotionCampaignMapper campaignMapper;
    @Autowired private ServiceEntitlementMapper entitlementMapper;
    @Autowired private ObjectMapper objectMapper;

    @Value("${ai.agent.enabled:true}")
    private boolean agentEnabled;

    @Value("${ai.agent.url:http://localhost:8090/agent/work-order}")
    private String agentUrl;

    @Value("${ai.agent.timeout-ms:30000}")
    private int timeoutMs;

    public Map<String, Object> generateWorkOrderResult(CustInteraction interaction) {
        Map<String, Object> payload = buildPayload(interaction);
        Map<String, Object> result;

        if (!agentEnabled) {
            result = fallbackResult(payload, "AI Agent service disabled");
        } else {
            try {
                result = callAgentService(payload);
            } catch (RestClientException e) {
                log.warn("AI Agent service unavailable, fallback to local result: {}", e.getMessage());
                result = fallbackResult(payload, e.getMessage());
            }
        }

        String replyDraft = safeString(result.get("reply_draft"));
        CustInteraction update = new CustInteraction();
        update.setId(interaction.getId());
        if (!replyDraft.isEmpty()) {
            update.setAiSuggestedReply(replyDraft);
        }
        update.setDetectedIntent(safeString(result.get("intent")));
        update.setEvidenceSufficiency(safeString(result.get("evidence_sufficiency")));
        update.setAgentResultJson(toJson(result));
        interactionMapper.updateById(update);

        return result;
    }

    private Map<String, Object> callAgentService(Map<String, Object> payload) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        RestTemplate restTemplate = new RestTemplate(factory);
        Map response = restTemplate.postForObject(agentUrl, payload, Map.class);
        if (response == null) {
            throw new RestClientException("empty response");
        }
        response.put("fallback", false);
        return response;
    }

    private Map<String, Object> buildPayload(CustInteraction interaction) {
        Long custId = interaction.getCustId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("interactionId", interaction.getId());
        payload.put("content", interaction.getContent());
        payload.put("interactionType", interaction.getInteractionType());
        payload.put("intentOverride", interaction.getIntentOverride());

        CustomerInfo customer = customerMapper.selectById(custId);
        payload.put("customer", mapCustomer(customer));

        CustRfmSnapshot rfm = rfmMapper.selectOne(
                new QueryWrapper<CustRfmSnapshot>()
                        .eq("cust_id", custId)
                        .orderByDesc("snapshot_date")
                        .last("LIMIT 1")
        );
        payload.put("rfm", mapRfm(rfm));

        List<ProductInfo> products = productMapper.selectList(
                new QueryWrapper<ProductInfo>()
                        .eq("status", 1)
                        .orderByDesc("create_time")
                        .last("LIMIT 30")
        );
        payload.put("products", mapProducts(products));

        List<OrderInfo> orders = orderMapper.selectList(
                new QueryWrapper<OrderInfo>()
                        .eq("cust_id", custId)
                        .orderByDesc("order_date")
                        .last("LIMIT 10")
        );
        payload.put("orders", mapOrders(orders));

        List<CustInteraction> history = interactionMapper.selectList(
                new QueryWrapper<CustInteraction>()
                        .eq("cust_id", custId)
                        .ne(interaction.getId() != null, "id", interaction.getId())
                        .orderByDesc("create_time")
                        .last("LIMIT 10")
        );
        payload.put("history", mapHistory(history));

        List<PromotionCampaign> campaigns = campaignMapper.selectList(
                new QueryWrapper<PromotionCampaign>()
                        .eq("status", 1)
                        .le("start_time", LocalDateTime.now())
                        .ge("end_time", LocalDateTime.now())
        );
        payload.put("campaigns", mapCampaigns(campaigns));

        List<ServiceEntitlement> entitlements = entitlementMapper.selectList(
                new QueryWrapper<ServiceEntitlement>().eq("status", 1)
        );
        payload.put("entitlements", mapEntitlements(entitlements));

        return payload;
    }

    private Map<String, Object> mapCustomer(CustomerInfo customer) {
        Map<String, Object> map = new HashMap<>();
        if (customer == null) return map;
        map.put("custId", customer.getCustId());
        map.put("custName", customer.getCustName());
        map.put("gender", customer.getGender());
        map.put("phone", customer.getPhone());
        map.put("birthday", customer.getBirthday() == null ? null : customer.getBirthday().toString());
        return map;
    }

    private Map<String, Object> mapRfm(CustRfmSnapshot rfm) {
        Map<String, Object> map = new HashMap<>();
        if (rfm == null) return map;
        map.put("rScore", rfm.getRScore());
        map.put("fScore", rfm.getFScore());
        map.put("mScore", rfm.getMScore());
        map.put("customerLevel", rfm.getCustomerLevel());
        map.put("valueTier", rfm.getValueTier());
        map.put("lifecycleRisk", rfm.getLifecycleRisk());
        map.put("snapshotDate", rfm.getSnapshotDate() == null ? null : rfm.getSnapshotDate().toString());
        return map;
    }

    private List<Map<String, Object>> mapProducts(List<ProductInfo> products) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProductInfo product : products) {
            Map<String, Object> map = new HashMap<>();
            map.put("productId", product.getProductId());
            map.put("productName", product.getProductName());
            map.put("category", product.getCategory());
            map.put("price", decimalToString(product.getPrice()));
            map.put("stock", product.getStock());
            map.put("features", product.getFeatures());
            map.put("status", product.getStatus());
            list.add(map);
        }
        return list;
    }

    private List<Map<String, Object>> mapOrders(List<OrderInfo> orders) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (OrderInfo order : orders) {
            Map<String, Object> map = new HashMap<>();
            map.put("orderId", order.getOrderId());
            map.put("productName", order.getProductName());
            map.put("orderAmount", decimalToString(order.getOrderAmount()));
            map.put("quantity", order.getQuantity());
            map.put("orderDate", order.getOrderDate() == null ? null : order.getOrderDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            list.add(map);
        }
        return list;
    }

    private List<Map<String, Object>> mapHistory(List<CustInteraction> history) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (CustInteraction item : history) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("interactionType", item.getInteractionType());
            map.put("content", item.getContent());
            map.put("status", item.getStatus());
            map.put("createTime", item.getCreateTime() == null ? null : item.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            list.add(map);
        }
        return list;
    }

    private List<Map<String, Object>> mapCampaigns(List<PromotionCampaign> campaigns) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PromotionCampaign item : campaigns) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("campaignName", item.getCampaignName());
            map.put("campaignType", item.getCampaignType());
            map.put("description", item.getDescription());
            map.put("allowedReplyText", item.getAllowedReplyText());
            map.put("targetValueTier", item.getTargetValueTier());
            map.put("targetLifecycleRisk", item.getTargetLifecycleRisk());
            map.put("productCategory", item.getProductCategory());
            map.put("minOrderAmount", decimalToString(item.getMinOrderAmount()));
            map.put("discountAmount", decimalToString(item.getDiscountAmount()));
            map.put("startTime", item.getStartTime() == null ? null : item.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            map.put("endTime", item.getEndTime() == null ? null : item.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            map.put("status", item.getStatus());
            list.add(map);
        }
        return list;
    }

    private List<Map<String, Object>> mapEntitlements(List<ServiceEntitlement> entitlements) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ServiceEntitlement item : entitlements) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("entitlementName", item.getEntitlementName());
            map.put("description", item.getDescription());
            map.put("allowedReplyText", item.getAllowedReplyText());
            map.put("applicableValueTier", item.getApplicableValueTier());
            map.put("applicableIntent", item.getApplicableIntent());
            map.put("responseSla", item.getResponseSla());
            map.put("priorityLevel", item.getPriorityLevel());
            map.put("requiresManualApproval", item.getRequiresManualApproval());
            map.put("maxPromiseLevel", item.getMaxPromiseLevel());
            map.put("status", item.getStatus());
            list.add(map);
        }
        return list;
    }

    private Map<String, Object> fallbackResult(Map<String, Object> payload, String reason) {
        Map<String, Object> profile = (Map<String, Object>) payload.getOrDefault("customer", new HashMap<>());
        String content = safeString(payload.get("content"));
        String name = safeString(profile.get("custName"));
        if (name.isEmpty()) name = "客户";

        String intent = content.contains("退") || content.contains("售后") ? "after_sales"
                : content.contains("投诉") || content.contains("差") || content.contains("没发货") ? "complaint"
                : content.contains("买") || content.contains("推荐") || content.contains("有没有") ? "product_consulting"
                : "general_service";
        String reply = name + "您好，您的问题我已经记录。当前 AI Agent 服务暂时不可用，我会先基于客户资料和业务规则进行人工核查，确认后给出明确处理方案。";

        Map<String, Object> result = new HashMap<>();
        result.put("intent", intent);
        result.put("customer_profile", payload.get("rfm"));
        result.put("retrieved_evidence", new ArrayList<>());
        result.put("reply_plan", List.of("记录客户诉求", "核查业务数据", "转人工确认后回复"));
        result.put("risk_warnings", List.of("AI Agent 服务不可用，已启用降级话术。原因：" + reason));
        result.put("reply_draft", reply);
        result.put("available_campaigns", new ArrayList<>());
        result.put("service_entitlements", new ArrayList<>());
        result.put("retrieved_docs", new ArrayList<>());
        result.put("internal_actions", List.of("记录客户诉求", "核查业务数据", "转人工确认后回复"));
        result.put("risk_flags", List.of(Map.of("level", "high", "message", "AI Agent 服务不可用，已启用人工核实流程。")));
        result.put("evidence_sufficiency", "insufficient");
        result.put("fallback", true);
        result.put("trace", List.of(
                Map.of("node", "Fallback", "status", "degraded", "detail", reason)
        ));
        return result;
    }

    private String toJson(Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.warn("Unable to serialize Agent result: {}", e.getMessage());
            return null;
        }
    }

    private String decimalToString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
