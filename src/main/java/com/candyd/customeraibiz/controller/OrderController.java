package com.candyd.customeraibiz.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.CustomerInfo;
import com.candyd.customeraibiz.entity.OrderInfo;
import com.candyd.customeraibiz.mapper.CustomerInfoMapper;
import com.candyd.customeraibiz.mapper.OrderInfoMapper;
import com.candyd.customeraibiz.vo.OrderListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/order")
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class OrderController {

    @Autowired
    private OrderInfoMapper orderMapper;

    @Autowired
    private CustomerInfoMapper customerInfoMapper;


    /**
     * 🔥 新增：将订单状态改为“已完成” (1)
     */
    @PostMapping("/finish/{orderId}")
    public String finishOrder(@PathVariable String orderId) {
        OrderInfo order = new OrderInfo();
        order.setOrderId(orderId);
        order.setOrderStatus(1); // 1 代表已完成
        orderMapper.updateById(order);
        return "success";
    }

    /**
     * 获取所有订单列表 (包含统计数据)
     * URL: /order/list
     */
    @GetMapping("/list")
    public Map<String, Object> getAllOrders() {
        // 1. 查出所有订单，按时间倒序排 (最新的在最上面)
        List<OrderInfo> orders = orderMapper.selectList(
                new QueryWrapper<OrderInfo>().orderByDesc("order_date")
        );

        List<OrderListVO> voList = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO; // 用来统计总营收

        // 2. 遍历每一条订单，把数据搬到 VO 里
        for (OrderInfo order : orders) {
            OrderListVO vo = new OrderListVO();

            // 搬运基本数据
            vo.setOrderId(order.getOrderId());
            vo.setCustId(order.getCustId());
            vo.setOrderAmount(order.getOrderAmount());
            vo.setOrderDate(order.getOrderDate());
            vo.setOrderStatus(order.getOrderStatus());

            // 搬运新加的三个字段 (防止空指针，给点默认值)
            vo.setProductName(order.getProductName() == null ? "未知商品" : order.getProductName());
            vo.setQuantity(order.getQuantity() == null ? 1 : order.getQuantity());
            vo.setUnitPrice(order.getUnitPrice() == null ? order.getOrderAmount() : order.getUnitPrice());

            // 3. 核心逻辑：拿着 custId 去查客户名字
            CustomerInfo cust = customerInfoMapper.selectById(order.getCustId());
            if (cust != null) {
                vo.setCustName(cust.getCustName());
            } else {
                vo.setCustName("未知客户");
            }

            // 4. 累加总金额 (为了给前端仪表盘展示)
            if (order.getOrderAmount() != null) {
                totalRevenue = totalRevenue.add(order.getOrderAmount());
            }

            voList.add(vo);
        }

        // 5. 打包返回：既有列表，又有总数
        Map<String, Object> result = new HashMap<>();
        result.put("items", voList);             // 列表数据
        result.put("totalCount", voList.size()); // 总单数
        result.put("totalRevenue", totalRevenue); // 总营收

        return result;
    }
}