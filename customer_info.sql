create table customer_info
(
    cust_id   bigint      not null comment '客户ID'
        primary key,
    cust_name varchar(50) null comment '姓名',
    gender    varchar(10) null comment '性别',
    phone     varchar(20) null comment '手机号',
    birthday  date        null comment '生日'
);

