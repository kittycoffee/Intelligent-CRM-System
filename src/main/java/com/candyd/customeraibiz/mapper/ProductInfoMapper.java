package com.candyd.customeraibiz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.candyd.customeraibiz.entity.ProductInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductInfoMapper extends BaseMapper<ProductInfo> {
    // MyBatis-Plus 已经内置了 CRUD，这里暂时不需要写 SQL
}