package com.candyd.customeraibiz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.candyd.customeraibiz.entity.RfmRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * RFM规则Mapper - 负责去数据库里搬运评分标准
 */
@Mapper
public interface RfmRuleMapper extends BaseMapper<RfmRule> {
    // 继承了 BaseMapper，就相当于拥有了自动化的增删改查指南
}