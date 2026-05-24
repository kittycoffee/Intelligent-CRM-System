create table product_info
(
    product_id   int auto_increment
        primary key,
    product_name varchar(100)                       not null comment '商品名称',
    category     varchar(50)                        null comment '分类，如：电子/食品/服装',
    price        decimal(10, 2)                     null comment '单价',
    stock        int                                null comment '库存',
    features     varchar(255)                       null comment '卖点关键词，供AI参考',
    status       tinyint  default 1                 null comment '1:上架 0:下架',
    create_time  datetime default CURRENT_TIMESTAMP null
);

