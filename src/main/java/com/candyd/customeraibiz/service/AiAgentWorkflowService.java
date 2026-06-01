package com.candyd.customeraibiz.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.CustInteraction;
import com.candyd.customeraibiz.entity.CustRfmSnapshot;
import com.candyd.customeraibiz.entity.CustomerInfo;
import com.candyd.customeraibiz.entity.OrderInfo;
import com.candyd.customeraibiz.entity.ProductInfo;
import com.candyd.customeraibiz.mapper.CustInteractionMapper;
import com.candyd.customeraibiz.mapper.CustRfmSnapshotMapper;
import com.candyd.customeraibiz.mapper.CustomerInfoMapper;
import com.candyd.customeraibiz.mapper.OrderInfoMapper;
import com.candyd.customeraibiz.mapper.ProductInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
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
        if (!replyDraft.isEmpty()) {
            CustInteraction update = new CustInteraction();
            update.setId(interaction.getId());
            update.setAiSuggestedReply(replyDraft);
            interactionMapper.updateById(update);
        }

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
        result.put("confidence", 0.35);
        result.put("fallback", true);
        result.put("trace", List.of(
                Map.of("node", "Fallback", "status", "degraded", "detail", reason)
        ));
        return result;
    }

    private String decimalToString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
