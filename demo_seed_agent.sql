-- Optional demo data for the AI customer operations work-order flow.
-- Import after the existing schema scripts.

INSERT INTO customer_info (cust_id, cust_name, gender, phone, birthday)
VALUES (9001, '演示客户', '女', '13800009001', '1998-08-18')
ON DUPLICATE KEY UPDATE cust_name = VALUES(cust_name);

INSERT INTO cust_rfm_snapshot (cust_id, r_score, f_score, m_score, customer_level, snapshot_date)
VALUES (9001, 4, 5, 5, '重要价值客户', CURRENT_DATE);

UPDATE cust_rfm_snapshot
SET value_tier = 'high', lifecycle_risk = 'churn_risk'
WHERE cust_id = 9001;

INSERT INTO promotion_campaign
  (campaign_name, campaign_type, description, allowed_reply_text, target_value_tier,
   target_lifecycle_risk, product_category, min_order_amount, discount_amount, start_time, end_time, status)
VALUES
  ('老客复购专享券', 'coupon', '面向存在流失风险的老客户，客服可推荐活动，资格以系统查询结果为准。',
   '您当前可参与老客复购专享活动，具体优惠请以结算页面展示为准。',
   'all', 'churn_risk', NULL, 199.00, 30.00, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1),
  ('数码焕新活动', 'discount', '适用于数码商品咨询场景的限时活动。',
   '当前数码商品可参与焕新活动，具体适用商品和优惠请以结算页面展示为准。',
   'all', 'all', '数码', 299.00, 20.00, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1);

INSERT INTO service_entitlement
  (entitlement_name, description, allowed_reply_text, applicable_value_tier, applicable_intent,
   response_sla, priority_level, requires_manual_approval, max_promise_level, status)
VALUES
  ('高价值客户优先处理', '高价值客户投诉和售后问题优先进入客服跟进队列。',
   '我们会优先为您核实并跟进处理进度。', 'high', 'all', '2小时内响应', 'high', 0, 'priority', 1),
  ('售后快速通道', '售后问题可建议客服核实订单后进入快速处理通道。',
   '核实订单信息后，我们会按售后快速通道为您跟进。', 'high', 'after_sales',
   '2小时内响应', 'high', 0, 'replacement', 1),
  ('补偿资格人工确认', '涉及补偿券时必须由客服人工确认，不允许 AI 直接承诺。',
   '关于补偿方案，我们会在核实后向您同步处理结果。', 'all', 'complaint',
   NULL, 'normal', 1, 'compensation', 1);

INSERT INTO product_info (product_name, category, price, stock, features, status)
SELECT '通勤降噪耳机', '数码', 299.00, 30, '主动降噪，轻量佩戴，适合地铁通勤', 1
WHERE NOT EXISTS (SELECT 1 FROM product_info WHERE product_name = '通勤降噪耳机');

INSERT INTO product_info (product_name, category, price, stock, features, status)
SELECT '轻薄羽绒服', '服装', 599.00, 12, '保暖轻便，适合冬季通勤', 1
WHERE NOT EXISTS (SELECT 1 FROM product_info WHERE product_name = '轻薄羽绒服');

INSERT INTO cust_interaction (cust_id, interaction_type, content, status)
VALUES
  (9001, '咨询', '我每天坐地铁，想买一款通勤耳机，有没有推荐？', 0),
  (9001, '投诉', '我上次买的东西到现在还没发货，体验太差了', 0),
  (9001, '售后', '商品包装还在，可以申请退货吗？', 0);
