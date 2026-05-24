create table sys_user
(
    user_id  int auto_increment
        primary key,
    username varchar(50) not null comment '账号',
    password varchar(50) not null comment '密码(明文演示用)',
    role     varchar(20) not null comment '角色: admin/user',
    nickname varchar(50) null comment '昵称'
)
    comment '系统用户表';

