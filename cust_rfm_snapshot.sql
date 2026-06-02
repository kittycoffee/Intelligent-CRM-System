create table cust_rfm_snapshot
(
    id             bigint auto_increment
        primary key,
    cust_id        bigint                             not null comment '客户ID',
    r_score        int                                null comment '近度得分',
    f_score        int                                null comment '频度得分',
    m_score        int                                null comment '额度得分',
    customer_level varchar(50)                        null comment '最终价值分类(如:重要价值客户)',
    value_tier     varchar(20) default 'normal'       null comment '价值等级:high/normal',
    lifecycle_risk varchar(20) default 'active'       null comment '生命周期风险:active/silent/churn_risk',
    snapshot_date  date                               not null comment '分析日期',
    create_time    datetime default CURRENT_TIMESTAMP null
)
    comment '客户价值分析结果快照表';

create index idx_snapshot
    on cust_rfm_snapshot (cust_id, snapshot_date);
