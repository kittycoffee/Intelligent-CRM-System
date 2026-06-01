from __future__ import annotations

import json
from pathlib import Path

from agent_core import run_agent_workflow


ROOT = Path(__file__).resolve().parent

BASE_PAYLOAD = {
    "customer": {"custId": 1001, "custName": "张女士", "gender": "女"},
    "rfm": {"rScore": 4, "fScore": 4, "mScore": 5, "customerLevel": "重要价值客户"},
    "products": [
        {
            "productId": 1,
            "productName": "通勤降噪耳机",
            "category": "数码",
            "features": "主动降噪，轻量佩戴，适合地铁通勤",
            "price": "299.00",
            "stock": 30,
            "status": 1,
        },
        {
            "productId": 2,
            "productName": "轻薄羽绒服",
            "category": "服装",
            "features": "保暖轻便，适合冬季通勤",
            "price": "599.00",
            "stock": 12,
            "status": 1,
        },
    ],
    "orders": [],
    "history": [],
}


def evaluate_case(case: dict) -> dict:
    payload = dict(BASE_PAYLOAD)
    payload["content"] = case["content"]
    payload["interactionType"] = case.get("interaction_type")
    if case.get("no_products"):
        payload["products"] = []

    result = run_agent_workflow(payload)
    checks = {
        "intent": result.intent == case["expected_intent"],
        "structured_output": bool(result.trace and result.reply_plan and result.reply_draft),
        "risk_guard": True,
    }
    if case.get("expect_product"):
        checks["product_evidence"] = any(e["source"] == "product" for e in result.retrieved_evidence)
    if case.get("expect_no_fabrication"):
        checks["risk_guard"] = any("禁止编造" in warning for warning in result.risk_warnings)
    if case.get("expect_policy"):
        checks["policy_evidence"] = any(e["source"] in {"policy", "faq"} for e in result.retrieved_evidence)

    return {
        "id": case["id"],
        "passed": all(checks.values()),
        "checks": checks,
        "intent": result.intent,
        "confidence": result.confidence,
    }


def main() -> None:
    cases = json.loads((ROOT / "data" / "eval_cases.json").read_text(encoding="utf-8"))
    results = [evaluate_case(case) for case in cases]
    passed = sum(1 for result in results if result["passed"])
    report = {
        "total": len(results),
        "passed": passed,
        "pass_rate": round(passed / len(results), 4),
        "results": results,
    }
    report_path = ROOT / "data" / "evaluation_report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"total": len(results), "passed": passed, "pass_rate": report["pass_rate"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
