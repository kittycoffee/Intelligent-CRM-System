create table promotion_campaign
(
    id                    bigint auto_increment primary key,
    campaign_name         varchar(100)                       not null comment '活动名称',
    campaign_type         varchar(30)                        not null comment 'coupon/discount/recommendation',
    description           varchar(500)                       null comment '活动说明',
    allowed_reply_text    varchar(500)                       not null comment '允许 AI 对客户使用的话术',
    target_value_tier     varchar(20) default 'all'          not null comment 'high/normal/all',
    target_lifecycle_risk varchar(20) default 'all'          not null comment 'active/silent/churn_risk/all',
    product_category      varchar(50)                        null comment '适用商品分类，为空表示不限',
    min_order_amount      decimal(10, 2) default 0           null comment '最低订单金额',
    discount_amount       decimal(10, 2)                     null comment '优惠金额',
    start_time            datetime                           not null,
    end_time              datetime                           not null,
    status                tinyint        default 1           not null comment '1启用 0停用',
    create_time           datetime       default CURRENT_TIMESTAMP null
)
    comment '客户运营活动配置';

create index idx_campaign_active
    on promotion_campaign (status, start_time, end_time);

