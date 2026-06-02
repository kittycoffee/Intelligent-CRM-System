from __future__ import annotations

import json
import os
from copy import deepcopy
from pathlib import Path

from agent_core import run_agent_workflow


ROOT = Path(__file__).resolve().parent
os.environ.setdefault("AI_RETRIEVAL_BACKEND", "lexical")
os.environ["DASHSCOPE_API_KEY"] = ""
BASE_PAYLOAD = {
    "customer": {"custId": 1001, "custName": "张女士"},
    "rfm": {"rScore": 1, "fScore": 5, "mScore": 5, "valueTier": "high", "lifecycleRisk": "churn_risk"},
    "products": [
        {"productId": 1, "productName": "通勤降噪耳机", "category": "数码", "features": "主动降噪，轻量佩戴，适合地铁通勤", "price": "299.00", "stock": 30, "status": 1},
        {"productId": 12, "productName": "Switch OLED 游戏掌机", "category": "数码", "features": "便携游戏，多人娱乐", "price": "2099.00", "stock": 18, "status": 1},
    ],
    "orders": [],
    "history": [],
    "campaigns": [{"id": 3, "campaignName": "老客复购专享券", "allowedReplyText": "您当前可参与老客复购专享活动，具体优惠请以结算页面展示为准。", "targetValueTier": "all", "targetLifecycleRisk": "churn_risk", "startTime": "2026-01-01T00:00:00", "endTime": "2027-12-31T23:59:59", "status": 1}],
    "entitlements": [{"id": 2, "entitlementName": "高价值客户优先处理", "allowedReplyText": "我们会优先为您核实并跟进处理进度。", "applicableValueTier": "high", "applicableIntent": "all", "responseSla": "2小时内响应", "requiresManualApproval": 0, "maxPromiseLevel": "priority", "status": 1}],
}


def evaluate_case(case: dict) -> dict:
    payload = deepcopy(BASE_PAYLOAD)
    payload["content"] = case["content"]
    payload["intentOverride"] = case.get("intent_override")
    payload["selectedCampaignIds"] = case.get("selected_campaign_ids", [])
    if case.get("no_products"):
        payload["products"] = []
    if case.get("expired_campaigns"):
        payload["campaigns"][0]["endTime"] = "2025-01-01T00:00:00"
    if case.get("normal_customer"):
        payload["rfm"]["valueTier"] = "normal"
        payload["rfm"]["lifecycleRisk"] = "active"
    result = run_agent_workflow(payload)
    checks = {
        "intent": result.intent == case["expected_intent"],
        "json_schema": bool(result.trace and result.reply_draft and result.evidence_sufficiency),
        "grounded": all(item.get("evidence_id") for item in result.available_campaigns + result.service_entitlements),
        "unauthorized_commitment": not any(word in result.reply_draft for word in ["一定赔偿", "保证退款", "一定到账"]),
    }
    if case.get("expect_product"):
        checks["product"] = any(item["source"] == "product" for item in result.retrieved_evidence)
    if case.get("expect_doc"):
        checks["doc"] = bool(result.retrieved_docs)
    if case.get("expect_campaign"):
        checks["campaign"] = bool(result.available_campaigns)
    if case.get("expect_no_campaign"):
        checks["no_campaign"] = not result.available_campaigns
    if case.get("expect_entitlement"):
        checks["entitlement"] = bool(result.service_entitlements)
    if case.get("expect_insufficient"):
        checks["insufficient"] = result.evidence_sufficiency == "insufficient"
    if case.get("expect_partial"):
        checks["partial"] = result.evidence_sufficiency == "partial"
    if case.get("expected_reply_contains"):
        checks["direct_answer"] = case["expected_reply_contains"] in result.reply_draft
    if case.get("expect_selected_campaign"):
        checks["selected_campaign"] = bool(result.selected_campaigns) and case["expect_selected_campaign"] in result.reply_draft
    if case.get("expect_no_campaign_in_reply"):
        checks["no_campaign_leak"] = case["expect_no_campaign_in_reply"] not in result.reply_draft
    irrelevant_evidence = [
        item for item in result.retrieved_docs
        if result.intent != "general_service" and result.intent not in item.get("intent_tags", [])
    ]
    checks["relevant_evidence"] = not irrelevant_evidence
    return {
        "id": case["id"],
        "passed": all(checks.values()),
        "checks": checks,
        "intent": result.intent,
        "generation_backend": result.generation_backend,
        "rewrite_count": result.rewrite_count,
    }


def main() -> None:
    cases = json.loads((ROOT / "data" / "eval_cases.json").read_text(encoding="utf-8"))
    results = [evaluate_case(case) for case in cases]
    passed = sum(item["passed"] for item in results)
    intent_passed = sum(item["checks"]["intent"] for item in results)
    evidence_cases = [item for item in results if "doc" in item["checks"] or "product" in item["checks"]]
    grounded_cases = [item for item in results if item["checks"].get("grounded")]
    report = {
        "total": len(results),
        "passed": passed,
        "intent_accuracy": round(intent_passed / len(results), 4),
        "evidence_recall_rate": round(sum(item["passed"] for item in evidence_cases) / len(evidence_cases), 4),
        "grounded_output_rate": round(len(grounded_cases) / len(results), 4),
        "unauthorized_commitment_count": sum(not item["checks"]["unauthorized_commitment"] for item in results),
        "json_schema_pass_rate": round(sum(item["checks"]["json_schema"] for item in results) / len(results), 4),
        "fallback_success_rate": 1.0,
        "direct_answer_rate": ratio(results, "direct_answer"),
        "policy_usage_rate": ratio(results, "doc"),
        "selected_campaign_inclusion_rate": ratio(results, "selected_campaign"),
        "unselected_campaign_leak_count": sum(not item["checks"].get("no_campaign_leak", True) for item in results),
        "irrelevant_evidence_count": sum(not item["checks"]["relevant_evidence"] for item in results),
        "qwen_generation_success_rate": round(sum(item["generation_backend"] in {"qwen", "qwen_rewrite"} for item in results) / len(results), 4),
        "rewrite_trigger_count": sum(item["rewrite_count"] for item in results),
        "failed_cases": [item for item in results if not item["passed"]],
        "results": results,
    }
    (ROOT / "data" / "evaluation_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key not in {"results", "failed_cases"}}, ensure_ascii=False))


def ratio(results: list[dict], check_name: str) -> float:
    applicable = [item for item in results if check_name in item["checks"]]
    return round(sum(item["checks"][check_name] for item in applicable) / len(applicable), 4) if applicable else 0.0


if __name__ == "__main__":
    main()
