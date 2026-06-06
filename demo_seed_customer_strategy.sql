-- Idempotent demo personas for customer value tier and lifecycle filtering.
-- Import this script, then call GET /rfm/analyze to rebuild strategy snapshots.
-- Only demo customer IDs 9101-9106 are replaced.

DELETE FROM ai_advice_history WHERE cust_id BETWEEN 9101 AND 9106;
DELETE FROM cust_rfm_snapshot WHERE cust_id BETWEEN 9101 AND 9106;
DELETE FROM cust_interaction WHERE cust_id BETWEEN 9101 AND 9106;
DELETE FROM order_info WHERE cust_id BETWEEN 9101 AND 9106;

INSERT INTO customer_info (cust_id, cust_name, gender, phone, birthday)
VALUES
  (9101, '演示客户-高价值活跃', '女', '13800009101', '1992-03-12'),
  (9102, '演示客户-高价值沉睡', '男', '13800009102', '1988-06-21'),
  (9103, '演示客户-高价值流失', '女', '13800009103', '1990-11-08'),
  (9104, '演示客户-普通沉睡', '男', '13800009104', '1998-01-17'),
  (9105, '演示客户-普通流失退款', '女', '13800009105', '1995-09-03'),
  (9106, '演示客户-普通流失不满', '男', '13800009106', '2000-04-26')
ON DUPLICATE KEY UPDATE
  cust_name = VALUES(cust_name),
  gender = VALUES(gender),
  phone = VALUES(phone),
  birthday = VALUES(birthday);

-- 9101: high + active. Five completed orders and a recent purchase.
INSERT INTO order_info
  (order_id, cust_id, order_amount, order_date, order_status, fulfillment_status, product_name, quantity, unit_price)
VALUES
  ('STRATEGY-9101-01', 9101, 1299.00, DATE_SUB(NOW(), INTERVAL 120 DAY), 1, 'signed', '通勤耳机', 1, 1299.00),
  ('STRATEGY-9101-02', 9101, 899.00, DATE_SUB(NOW(), INTERVAL 95 DAY), 1, 'signed', '智能手表', 1, 899.00),
  ('STRATEGY-9101-03', 9101, 699.00, DATE_SUB(NOW(), INTERVAL 70 DAY), 1, 'signed', '机械键盘', 1, 699.00),
  ('STRATEGY-9101-04', 9101, 1599.00, DATE_SUB(NOW(), INTERVAL 35 DAY), 1, 'signed', '轻薄羽绒服', 1, 1599.00),
  ('STRATEGY-9101-05', 9101, 799.00, DATE_SUB(NOW(), INTERVAL 8 DAY), 1, 'signed', '运动鞋', 1, 799.00);

-- 9102: high + silent. Historical spend exceeds 5000 yuan; latest order was 45 days ago.
INSERT INTO order_info
  (order_id, cust_id, order_amount, order_date, order_status, fulfillment_status, product_name, quantity, unit_price)
VALUES
  ('STRATEGY-9102-01', 9102, 5499.00, DATE_SUB(NOW(), INTERVAL 45 DAY), 1, 'signed', '游戏掌机', 1, 5499.00);

-- 9103: high + churn_risk. Historical spend exceeds 5000 yuan; no purchase for 90 days and a recent complaint.
INSERT INTO order_info
  (order_id, cust_id, order_amount, order_date, order_status, fulfillment_status, product_name, quantity, unit_price)
VALUES
  ('STRATEGY-9103-01', 9103, 6899.00, DATE_SUB(NOW(), INTERVAL 90 DAY), 1, 'signed', '旗舰手机', 1, 6899.00);

-- 9104: normal + silent. Low historical spend and latest order was 45 days ago.
INSERT INTO order_info
  (order_id, cust_id, order_amount, order_date, order_status, fulfillment_status, product_name, quantity, unit_price)
VALUES
  ('STRATEGY-9104-01', 9104, 199.00, DATE_SUB(NOW(), INTERVAL 80 DAY), 1, 'signed', '保温杯', 1, 199.00),
  ('STRATEGY-9104-02', 9104, 299.00, DATE_SUB(NOW(), INTERVAL 45 DAY), 1, 'signed', '双肩包', 1, 299.00);

-- 9105: normal + churn_risk. Low historical spend, no purchase for 90 days and a recent refund request.
INSERT INTO order_info
  (order_id, cust_id, order_amount, order_date, order_status, fulfillment_status, product_name, quantity, unit_price)
VALUES
  ('STRATEGY-9105-01', 9105, 259.00, DATE_SUB(NOW(), INTERVAL 120 DAY), 1, 'signed', '加湿器', 1, 259.00),
  ('STRATEGY-9105-02', 9105, 399.00, DATE_SUB(NOW(), INTERVAL 90 DAY), 1, 'signed', '电动牙刷', 1, 399.00);

-- 9106: normal + churn_risk. Low historical spend, no purchase for 120 days and a recent dissatisfaction record.
INSERT INTO order_info
  (order_id, cust_id, order_amount, order_date, order_status, fulfillment_status, product_name, quantity, unit_price)
VALUES
  ('STRATEGY-9106-01', 9106, 329.00, DATE_SUB(NOW(), INTERVAL 120 DAY), 1, 'signed', '蓝牙音箱', 1, 329.00);

INSERT INTO cust_interaction (cust_id, interaction_type, content, create_time, status)
VALUES
  (9103, '投诉', '历史订单迟迟没有处理完成，体验太差了，希望尽快解决。', DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
  (9105, '售后', '商品不太合适，我想申请退款，请协助处理。', DATE_SUB(NOW(), INTERVAL 6 DAY), 0),
  (9106, '回访', '这次服务让我很不满，后续暂时不考虑继续购买。', DATE_SUB(NOW(), INTERVAL 3 DAY), 0);
