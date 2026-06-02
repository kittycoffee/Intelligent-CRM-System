import os
import unittest
from unittest.mock import patch

from agent_core import run_agent_workflow

os.environ.setdefault("AI_RETRIEVAL_BACKEND", "lexical")
os.environ["DASHSCOPE_API_KEY"] = ""

BASE_PAYLOAD = {
    "interactionId": 1,
    "customer": {"custId": 1001, "custName": "张女士", "gender": "女"},
    "rfm": {
        "rScore": 1,
        "fScore": 5,
        "mScore": 5,
        "customerLevel": "重要价值客户",
        "valueTier": "high",
        "lifecycleRisk": "churn_risk",
    },
    "products": [
        {
            "productId": 1,
            "productName": "通勤降噪耳机",
            "category": "数码",
            "features": "主动降噪，轻量佩戴，适合地铁通勤",
            "price": "299.00",
            "stock": 30,
            "status": 1,
        }
    ],
    "orders": [],
    "history": [],
    "campaigns": [
        {
            "id": 3,
            "campaignName": "老客复购专享券",
            "allowedReplyText": "您当前可参与老客复购专享活动，具体优惠请以结算页面展示为准。",
            "targetValueTier": "all",
            "targetLifecycleRisk": "churn_risk",
            "startTime": "2026-01-01T00:00:00",
            "endTime": "2027-12-31T23:59:59",
            "status": 1,
        }
    ],
    "entitlements": [
        {
            "id": 2,
            "entitlementName": "高价值客户优先处理",
            "allowedReplyText": "我们会优先为您核实并跟进处理进度。",
            "applicableValueTier": "high",
            "applicableIntent": "all",
            "responseSla": "2小时内响应",
            "requiresManualApproval": 0,
            "maxPromiseLevel": "priority",
            "status": 1,
        }
    ],
}


class AgentCoreTest(unittest.TestCase):
    def test_product_consulting_has_grounded_product_and_campaign(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "我想买一款适合通勤的耳机，有没有推荐？"
        result = run_agent_workflow(payload)
        self.assertEqual(result.intent, "product_consulting")
        self.assertTrue(any(e["source"] == "product" for e in result.retrieved_evidence))
        self.assertIn("通勤降噪耳机", result.reply_draft)
        self.assertTrue(result.available_campaigns)

    def test_no_product_match_is_insufficient(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "有没有潜水用的专业设备推荐？"
        payload["products"] = []
        result = run_agent_workflow(payload)
        self.assertEqual(result.intent, "product_consulting")
        self.assertEqual(result.evidence_sufficiency, "insufficient")
        self.assertTrue(any("禁止编造" in warning for warning in result.risk_warnings))

    def test_high_value_complaint_uses_priority_boundary(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "订单一直没发货，体验太差了，我要投诉"
        result = run_agent_workflow(payload)
        self.assertEqual(result.intent, "complaint")
        self.assertEqual(result.customer_strategy["value_tier"], "high")
        self.assertIn("优先", result.reply_draft)
        self.assertNotIn("一定赔偿", result.reply_draft)
        self.assertNotIn("数码焕新活动", [item["campaign_name"] for item in result.available_campaigns])

    def test_refund_is_partial_and_never_promises_timeline(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "退款什么时候到账？"
        result = run_agent_workflow(payload)
        self.assertEqual(result.intent, "after_sales")
        self.assertEqual(result.evidence_sufficiency, "partial")
        self.assertTrue(any("不得承诺金额和具体到账时间" in warning for warning in result.risk_warnings))

    def test_staff_override_wins(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "我想咨询一下"
        payload["intentOverride"] = "after_sales"
        result = run_agent_workflow(payload)
        self.assertEqual(result.intent, "after_sales")

    def test_unselected_campaign_does_not_leak_into_template_reply(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "我想买一款适合通勤的耳机，有没有推荐？"
        result = run_agent_workflow(payload)
        self.assertTrue(result.available_campaigns)
        self.assertFalse(result.selected_campaigns)
        self.assertNotIn("老客复购专享活动", result.reply_draft)

    def test_selected_campaign_is_included_in_template_reply(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "我想买一款适合通勤的耳机，有没有推荐？"
        payload["selectedCampaignIds"] = [3]
        result = run_agent_workflow(payload)
        self.assertTrue(result.selected_campaigns)
        self.assertIn("老客复购专享活动", result.reply_draft)

    def test_game_console_consulting_rule(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "最近想入手一台游戏机，是买 Switch 还是别的？有现货吗？"
        result = run_agent_workflow(payload)
        self.assertEqual(result.intent, "product_consulting")

    def test_return_policy_guidance_is_customer_safe(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "商品包装还在，可以申请退货吗？"
        result = run_agent_workflow(payload)
        self.assertTrue(result.policy_guidance)
        self.assertIn("签收后七日内", result.reply_draft)

    def test_unselected_campaign_from_qwen_triggers_rewrite(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "我想买一款适合通勤的耳机，有没有推荐？"

        class FakeQwenClient:
            calls = 0

            def generate_json(self, system_prompt, user_prompt):
                self.__class__.calls += 1
                if self.__class__.calls == 1:
                    return {"reply_draft": "可以参加老客复购专享券。", "internal_actions": [], "used_evidence_ids": []}
                return {"reply_draft": "目前有通勤降噪耳机可供选择。", "internal_actions": [], "used_evidence_ids": ["product_1"]}

        with patch("agent_core.QwenClient", FakeQwenClient):
            result = run_agent_workflow(payload)
        self.assertEqual(result.generation_backend, "qwen_rewrite")
        self.assertEqual(result.rewrite_count, 1)
        self.assertNotIn("老客复购专享券", result.reply_draft)

    def test_selected_campaign_omission_from_qwen_triggers_rewrite(self):
        payload = dict(BASE_PAYLOAD)
        payload["content"] = "我想买一款适合通勤的耳机，有没有推荐？"
        payload["selectedCampaignIds"] = [3]

        class FakeQwenClient:
            calls = 0

            def generate_json(self, system_prompt, user_prompt):
                self.__class__.calls += 1
                if self.__class__.calls == 1:
                    return {"reply_draft": "目前有通勤降噪耳机可供选择。", "internal_actions": [], "used_evidence_ids": ["product_1"]}
                return {
                    "reply_draft": "目前有通勤降噪耳机可供选择。您当前可参与老客复购专享活动，具体优惠请以结算页面展示为准。",
                    "internal_actions": [],
                    "used_evidence_ids": ["product_1", "campaign_3"],
                }

        with patch("agent_core.QwenClient", FakeQwenClient):
            result = run_agent_workflow(payload)
        self.assertEqual(result.generation_backend, "qwen_rewrite")
        self.assertEqual(result.rewrite_count, 1)
        self.assertIn("老客复购专享活动", result.reply_draft)


if __name__ == "__main__":
    unittest.main()
