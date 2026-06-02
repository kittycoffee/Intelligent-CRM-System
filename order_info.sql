create table order_info
(
    order_id     varchar(50)                              not null comment '订单编号'
        primary key,
    cust_id      bigint                                   not null comment '关联客户ID',
    order_amount decimal(15, 2)                           not null comment '订单金额',
    order_date   datetime                                 not null comment '下单日期',
    order_status tinyint        default 1                 null comment '1:完成, 0:取消',
    fulfillment_status varchar(30) default 'pending_shipment' null comment 'pending_shipment/shipped/signed',
    logistics_status varchar(100) null comment '对客可见物流状态',
    shipped_time datetime null,
    signed_time datetime null,
    payment_method varchar(30) null,
    refund_status varchar(30) null,
    refund_apply_time datetime null,
    create_time  datetime       default CURRENT_TIMESTAMP null,
    product_name varchar(100)   default '通用商品'        null comment '商品名称',
    quantity     int            default 1                 null comment '购买数量',
    unit_price   decimal(10, 2) default 0.00              null comment '商品单价'
)
    comment '原始订单流水表';

create index idx_cust_id
    on order_info (cust_id);
