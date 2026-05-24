create table cust_interaction
(
    id                 bigint auto_increment comment '主键ID'
        primary key,
    cust_id            bigint                             not null comment '客户ID',
    interaction_type   varchar(50)                        null comment '交互类型：投诉/咨询/回访',
    content            text                               null comment '交互详情内容',
    create_time        datetime default CURRENT_TIMESTAMP null comment '发生时间',
    status             int      default 0                 null comment '状态:0待处理,1已处理',
    handle_result      text                               null comment '人工处理回复',
    ai_suggested_reply text                               null comment 'AI建议回复话术'
)
    comment '客户交互记录表';

