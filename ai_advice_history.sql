create table ai_advice_history
(
    id          bigint auto_increment
        primary key,
    cust_id     bigint                             not null,
    advice_text text                               not null comment 'AI生成的建议内容',
    prompt_used text                               null comment '发给AI的指令原文',
    create_time datetime default CURRENT_TIMESTAMP null
)
    comment 'AI辅助决策建议记录表';

