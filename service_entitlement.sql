create table service_entitlement
(
    id                       bigint auto_increment primary key,
    entitlement_name         varchar(100)                      not null comment '服务权益名称',
    description              varchar(500)                      null comment '权益说明',
    allowed_reply_text       varchar(500)                      not null comment '允许 AI 对客户承诺的边界话术',
    applicable_value_tier    varchar(20) default 'all'         not null comment 'high/normal/all',
    applicable_intent        varchar(30) default 'all'         not null comment 'complaint/after_sales/all',
    response_sla             varchar(50)                       null comment '响应时限',
    priority_level           varchar(20) default 'normal'      not null comment 'normal/high/urgent',
    requires_manual_approval tinyint     default 0             not null comment '1需客服人工确认',
    max_promise_level        varchar(20) default 'none'        not null comment 'none/priority/compensation/replacement',
    status                   tinyint     default 1             not null comment '1启用 0停用',
    create_time              datetime    default CURRENT_TIMESTAMP null
)
    comment '客服可用服务权益';

