-- Additive migration for order fulfillment context and work-order association.
alter table order_info
    add column fulfillment_status varchar(30) default 'pending_shipment' null comment 'pending_shipment/shipped/signed' after order_status,
    add column logistics_status varchar(100) null comment 'Customer-safe logistics status' after fulfillment_status,
    add column shipped_time datetime null after logistics_status,
    add column signed_time datetime null after shipped_time,
    add column payment_method varchar(30) null after signed_time,
    add column refund_status varchar(30) null after payment_method,
    add column refund_apply_time datetime null after refund_status;

alter table cust_interaction
    add column related_order_id varchar(50) null comment 'Associated order for work-order analysis' after cust_id;
