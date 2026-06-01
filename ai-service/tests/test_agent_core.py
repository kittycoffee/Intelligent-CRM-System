import unittest

from agent_core import run_agent_workflow


BASE_PAYLOAD = {
    "interactionId": 1,
    "customer": {"custId": 1001, "custName": "张女士", "gender": "女"},
    "rfm": {"rScore": 4, "fScore": 4, "mScore": 5, "customerLevel": "重要价值客户"},
    "products": [
        {
            "productId": 1,
            "productName": "通勤降噪耳机",
            "category": "数码",
            "features": "主动降噪，轻量佩戴，适合通勤",
            "price": "299.00",
            "stock": 30,
            "status": 1,
        }
    ],
    "orders": [],
    "history": [],
}


class AgentCoreTest(unittest.TestCase):
    def test_product_consulting_has_product_evidence(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "我想买一款适合通勤的耳机，有没有推荐？"
        result = run_agent_workflow(payload)
        self.assertEqual(result.intent, "product_consulting")
        self.assertTrue(any(e["source"] == "product" for e in result.retrieved_evidence))
        self.assertIn("通勤降噪耳机", result.reply_draft)

    def test_no_product_match_warns_against_fabrication(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "有没有潜水用的专业设备？"
        payload["products"] = []
        result = run_agent_workflow(payload)
        self.assertEqual(result.intent, "product_consulting")
        self.assertTrue(any("禁止编造" in warning for warning in result.risk_warnings))

    def test_complaint_uses_customer_profile(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "我上次买的东西到现在还没发货，体验太差了"
        payload["interactionType"] = "投诉"
        result = run_agent_workflow(payload)
        self.assertEqual(result.intent, "complaint")
        self.assertTrue(result.customer_profile["isVip"])
        self.assertIn("抱歉", result.reply_draft)


if __name__ == "__main__":
    unittest.main()
