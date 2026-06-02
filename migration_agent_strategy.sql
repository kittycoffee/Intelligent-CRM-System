-- Additive migration for existing installations.
alter table cust_rfm_snapshot
    add column value_tier varchar(20) default 'normal' null comment 'high/normal' after customer_level,
    add column lifecycle_risk varchar(20) default 'active' null comment 'active/silent/churn_risk' after value_tier;

alter table cust_interaction
    add column detected_intent varchar(30) null comment 'AI 自动识别意图' after interaction_type,
    add column intent_override varchar(30) null comment '客服人工修正意图' after detected_intent,
    add column agent_result_json json null comment 'Agent 完整结构化结果' after ai_suggested_reply,
    add column evidence_sufficiency varchar(20) null comment 'sufficient/partial/insufficient' after agent_result_json;

