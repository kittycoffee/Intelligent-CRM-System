-- Optional demo data for the AI customer operations work-order flow.
-- Import after the existing schema scripts.

INSERT INTO customer_info (cust_id, cust_name, gender, phone, birthday)
VALUES (9001, '演示客户', '女', '13800009001', '1998-08-18')
ON DUPLICATE KEY UPDATE cust_name = VALUES(cust_name);

INSERT INTO cust_rfm_snapshot (cust_id, r_score, f_score, m_score, customer_level, snapshot_date)
VALUES (9001, 4, 5, 5, '重要价值客户', CURRENT_DATE);

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
