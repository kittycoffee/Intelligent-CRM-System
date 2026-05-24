create table rfm_rule
(
    rule_id     int auto_increment
        primary key,
    item_type   char                               not null comment '规则类型: R, F, M',
    min_value   decimal(10, 2)                     not null comment '该档位最小值(包含)',
    max_value   decimal(10, 2)                     not null comment '该档位最大值(不包含)',
    score       int                                not null comment '对应分数(1-5)',
    create_time datetime default CURRENT_TIMESTAMP null
)
    comment 'RFM评分规则配置表';

