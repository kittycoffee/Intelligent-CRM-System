package com.candyd.customeraibiz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.candyd.customeraibiz.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    /**
     * 🔥 新增：统计最近 7 天每天的销售额
     * SQL 逻辑：按“月-日”分组，把金额加起来
     */
    @Select("SELECT DATE_FORMAT(order_date, '%m-%d') as dateStr, SUM(order_amount) as totalVal " +
            "FROM order_info " +
            "WHERE order_date >= #{startDate} " +
            "GROUP BY DATE_FORMAT(order_date, '%m-%d')")
    List<Map<String, Object>> selectLast7DaysSales(@Param("startDate") String startDate);
}