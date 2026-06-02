from __future__ import annotations

import json
import math
import re
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any, TypedDict

from handbook import load_handbook_chunks
from qdrant_retriever import semantic_scores


@dataclass
class Evidence:
    id: str
    source: str
    title: str
    text: str
    score: float
    evidence_type: str
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class AgentResult:
    intent: str
    customer_strategy: dict[str, str]
    customer_profile: dict[str, Any]
    available_campaigns: list[dict[str, Any]]
    service_entitlements: list[dict[str, Any]]
    retrieved_docs: list[dict[str, Any]]
    retrieved_evidence: list[dict[str, Any]]
    internal_actions: list[str]
    reply_plan: list[str]
    reply_draft: str
    risk_flags: list[dict[str, str]]
    risk_warnings: list[str]
    evidence_sufficiency: str
    trace: list[dict[str, Any]]
    orchestration_backend: str
    retrieval_backend: str


def _normalize(text: str) -> str:
    return (text or "").lower().strip()


def _tokens(text: str) -> set[str]:
    text = _normalize(text)
    zh_tokens: set[str] = set()
    for segment in re.findall(r"[\u4e00-\u9fff]+", text):
        zh_tokens.add(segment)
        zh_tokens.update(segment[i : i + 2] for i in range(len(segment) - 1))
    return zh_tokens | set(re.findall(r"[a-zA-Z0-9]+", text))


def _score(query: str, doc: str) -> float:
    q = _tokens(query)
    d = _tokens(doc)
    if not q or not d:
        return 0.0
    return len(q & d) / math.sqrt(len(q) * len(d))


def classify_intent(content: str, override: str | None = None) -> str:
    if override:
        return override
    text = _normalize(content)
    rules = [
        ("complaint", ["投诉", "不满", "太差", "生气", "没发货", "太慢", "一直没回复"]),
        ("after_sales", ["退货", "退款", "换货", "售后", "保修", "质量", "包装", "发票", "坏了"]),
        ("promotion_inquiry", ["优惠", "活动", "折扣", "券", "促销", "价格保护", "保价"]),
        ("product_consulting", ["推荐", "想买", "有没有", "适合", "商品", "价格", "库存"]),
    ]
    for intent, keywords in rules:
        if any(keyword in text for keyword in keywords):
            return intent
    return "general_service"


def _negative_history(history: list[dict[str, Any]]) -> bool:
    keywords = ["投诉", "退款", "不满", "太差", "生气"]
    return any(any(keyword in str(item.get("content") or "") for keyword in keywords) for item in history)


def build_customer_profile(payload: dict[str, Any]) -> dict[str, Any]:
    customer = payload.get("customer") or {}
    rfm = payload.get("rfm") or {}
    orders = payload.get("orders") or []
    history = payload.get("history") or []
    f_score = int(rfm.get("fScore") or rfm.get("f_score") or 0)
    m_score = int(rfm.get("mScore") or rfm.get("m_score") or 0)
    r_score = int(rfm.get("rScore") or rfm.get("r_score") or 0)
    value_tier = rfm.get("valueTier") or rfm.get("value_tier")
    lifecycle_risk = rfm.get("lifecycleRisk") or rfm.get("lifecycle_risk")
    if not value_tier:
        value_tier = "high" if f_score >= 4 or m_score >= 4 else "normal"
    if not lifecycle_risk:
        lifecycle_risk = "churn_risk" if r_score <= 2 and _negative_history(history) else ("silent" if r_score <= 2 else "active")
    return {
        "custId": customer.get("custId") or customer.get("cust_id"),
        "name": customer.get("custName") or customer.get("name") or "客户",
        "level": rfm.get("customerLevel") or rfm.get("customer_level") or "未分级",
        "value_tier": value_tier,
        "lifecycle_risk": lifecycle_risk,
        "recentOrderCount": len(orders),
        "recentInteractionCount": len(history),
    }


def _is_active_campaign(item: dict[str, Any], strategy: dict[str, str]) -> bool:
    if int(item.get("status", 1)) != 1:
        return False
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    for key, boundary in [("startTime", "start"), ("endTime", "end")]:
        raw = item.get(key) or item.get(key.replace("T", "_t").lower())
        if raw:
            value = datetime.fromisoformat(str(raw).replace("Z", "+00:00")).replace(tzinfo=None)
            if boundary == "start" and value > now:
                return False
            if boundary == "end" and value < now:
                return False
    return (
        item.get("targetValueTier", item.get("target_value_tier", "all")) in {"all", strategy["value_tier"]}
        and item.get("targetLifecycleRisk", item.get("target_lifecycle_risk", "all")) in {"all", strategy["lifecycle_risk"]}
    )


def resolve_structured_facts(payload: dict[str, Any], strategy: dict[str, str], intent: str) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    campaigns = []
    for item in payload.get("campaigns") or []:
        if _is_active_campaign(item, strategy):
            campaign = dict(item)
            campaign["campaign_id"] = campaign.get("id") or campaign.get("campaignId")
            campaign["campaign_name"] = campaign.get("campaignName") or campaign.get("campaign_name")
            campaign["evidence_id"] = f"campaign_{campaign['campaign_id']}"
            campaign["evidence_type"] = "mysql"
            campaigns.append(campaign)
    entitlements = []
    for item in payload.get("entitlements") or []:
        tier = item.get("applicableValueTier", item.get("applicable_value_tier", "all"))
        applicable_intent = item.get("applicableIntent", item.get("applicable_intent", "all"))
        if int(item.get("status", 1)) == 1 and tier in {"all", strategy["value_tier"]} and applicable_intent in {"all", intent}:
            entitlement = dict(item)
            entitlement["entitlement_id"] = entitlement.get("id") or entitlement.get("entitlementId")
            entitlement["name"] = entitlement.get("entitlementName") or entitlement.get("name")
            entitlement["evidence_id"] = f"entitlement_{entitlement['entitlement_id']}"
            entitlement["evidence_type"] = "mysql"
            entitlements.append(entitlement)
    return campaigns, entitlements


def _product_evidence(payload: dict[str, Any], query: str) -> list[Evidence]:
    scored: list[Evidence] = []
    for product in payload.get("products") or []:
        if int(product.get("status", 1)) != 1 or int(product.get("stock") or 0) <= 0:
            continue
        name = product.get("productName") or product.get("name") or "未命名商品"
        text = f"{name} {product.get('category') or ''} {product.get('features') or ''}"
        score = _score(query, text)
        if score >= 0.04:
            scored.append(Evidence(
                id=f"product_{product.get('productId') or name}",
                source="product",
                title=name,
                text=text,
                score=round(score, 4),
                evidence_type="mysql",
                metadata=product,
            ))
    return sorted(scored, key=lambda item: item.score, reverse=True)


def retrieve_handbook(query: str, intent: str, top_k: int = 5) -> tuple[list[Evidence], str]:
    docs = load_handbook_chunks()
    semantic, backend = semantic_scores(query, docs)
    scored: list[Evidence] = []
    for doc in docs:
        lexical = _score(query, f"{doc['title']} {doc['text']}")
        dense = semantic.get(doc["id"], 0.0)
        score = lexical + (0.15 if intent in doc.get("intent_tags", []) else 0.0)
        if dense:
            score = max(score, dense + 0.05)
        if score >= 0.08:
            metadata = dict(doc)
            metadata["retrieval_mode"] = "qdrant" if dense else "keyword"
            scored.append(Evidence(
                id=doc["id"],
                source=doc["type"],
                title=doc["title"],
                text=doc["text"],
                score=round(score, 4),
                evidence_type=metadata["retrieval_mode"],
                metadata=metadata,
            ))
    scored.sort(key=lambda item: item.score, reverse=True)
    return scored[:top_k], backend


def build_internal_actions(intent: str, strategy: dict[str, str], campaigns: list[dict[str, Any]], entitlements: list[dict[str, Any]]) -> list[str]:
    actions = ["建议客服先核实订单、历史工单和客户当前诉求。"]
    if strategy["value_tier"] == "high":
        actions.append("该客户为高价值客户，建议优先跟进并记录处理进度。")
    if strategy["lifecycle_risk"] == "churn_risk":
        actions.append("该客户存在流失风险，可推荐当前可用活动，但补偿资格需人工确认。")
    if intent == "complaint":
        actions.append("建议将投诉工单标记为高优先级，避免直接承诺退款或赔偿。")
    if intent == "after_sales":
        actions.append("建议客服核实订单状态后按售后规则处理。")
    if campaigns:
        actions.append("建议客服核实后使用已配置优惠方案：" + "、".join(item["campaign_name"] for item in campaigns))
    for item in entitlements:
        if item.get("requiresManualApproval") or item.get("requires_manual_approval"):
            actions.append(f"{item['name']}需要人工确认后再向客户同步。")
    return actions


def build_risk_flags(intent: str, content: str, products: list[Evidence], docs: list[Evidence], entitlements: list[dict[str, Any]]) -> list[dict[str, str]]:
    flags: list[dict[str, str]] = []
    if intent == "product_consulting" and not products:
        flags.append({"level": "high", "message": "未检索到可推荐的在售商品，禁止编造商品名称或库存。"})
    if intent in {"complaint", "after_sales"} and not docs:
        flags.append({"level": "high", "message": "未召回售后规则依据，只能转人工核实。"})
    if any(word in content for word in ["退款", "赔偿", "到账"]):
        flags.append({"level": "medium", "message": "涉及退款或赔偿时，不得承诺金额和具体到账时间。"})
    if any(item.get("requiresManualApproval") or item.get("requires_manual_approval") for item in entitlements):
        flags.append({"level": "medium", "message": "存在需要客服人工确认的补偿资格，AI 不得直接承诺。"})
    return flags


def evidence_sufficiency(intent: str, products: list[Evidence], docs: list[Evidence], flags: list[dict[str, str]]) -> str:
    if intent == "product_consulting" and not products:
        return "insufficient"
    if intent in {"complaint", "after_sales", "promotion_inquiry"} and not docs:
        return "insufficient"
    if any(flag["level"] == "medium" for flag in flags):
        return "partial"
    return "sufficient"


def compose_reply(intent: str, profile: dict[str, Any], products: list[Evidence], campaigns: list[dict[str, Any]], entitlements: list[dict[str, Any]]) -> str:
    prefix = f"{profile['name']}您好，"
    allowed_entitlement = next(
        (item for item in entitlements if not (item.get("requiresManualApproval") or item.get("requires_manual_approval"))),
        None,
    )
    entitlement_text = ""
    if allowed_entitlement:
        entitlement_text = allowed_entitlement.get("allowedReplyText") or allowed_entitlement.get("allowed_reply_text") or ""
    campaign_text = ""
    if campaigns:
        campaign_text = campaigns[0].get("allowedReplyText") or campaigns[0].get("allowed_reply_text") or ""
    if intent == "complaint":
        return prefix + "非常抱歉给您带来不好的体验。我们会先核实订单和历史处理记录。" + entitlement_text + "涉及补偿、退款或赔偿的部分，我们会在核实后向您同步处理结果。"
    if intent == "after_sales":
        return prefix + "我们已经记录您的售后诉求，请提供订单号、商品状态和必要凭证。" + entitlement_text + "具体退换货或退款结果需要结合订单状态核实。"
    if intent == "promotion_inquiry":
        return prefix + (campaign_text or "暂未查询到适用于您的有效活动，我们会继续为您核实。")
    if intent == "product_consulting":
        if not products:
            return prefix + "暂未查询到与您需求匹配的在售商品。为避免推荐不准确，请补充预算、使用场景和偏好。"
        product = products[0]
        suffix = f"根据您的需求，可以优先了解“{product.title}”。该商品当前有库存，主要特点是{product.metadata.get('features') or product.text}。"
        return prefix + suffix + (campaign_text if campaign_text else "")
    return prefix + "您的问题已经记录，我们会结合订单和业务规则核实后给出明确处理建议。"


class WorkflowState(TypedDict, total=False):
    payload: dict[str, Any]
    intent: str
    profile: dict[str, Any]
    strategy: dict[str, str]
    products: list[Evidence]
    docs: list[Evidence]
    campaigns: list[dict[str, Any]]
    entitlements: list[dict[str, Any]]
    actions: list[str]
    flags: list[dict[str, str]]
    sufficiency: str
    draft: str
    retrieval_backend: str
    trace: list[dict[str, Any]]


def _with_trace(state: WorkflowState, node: str, detail: Any) -> list[dict[str, Any]]:
    return [*(state.get("trace") or []), {"node": node, "status": "success", "detail": detail}]


def _intent_node(state: WorkflowState) -> WorkflowState:
    payload = state["payload"]
    intent = classify_intent(payload.get("content") or "", payload.get("intentOverride"))
    return {"intent": intent, "trace": _with_trace(state, "Intent Classifier", f"intent={intent}")}


def _profile_node(state: WorkflowState) -> WorkflowState:
    profile = build_customer_profile(state["payload"])
    strategy = {"value_tier": profile["value_tier"], "lifecycle_risk": profile["lifecycle_risk"]}
    return {"profile": profile, "strategy": strategy, "trace": _with_trace(state, "Customer Strategy Profiler", strategy)}


def _resolver_node(state: WorkflowState) -> WorkflowState:
    campaigns, entitlements = resolve_structured_facts(state["payload"], state["strategy"], state["intent"])
    return {
        "campaigns": campaigns,
        "entitlements": entitlements,
        "trace": _with_trace(state, "Offer & Entitlement Resolver", f"{len(campaigns)} campaigns, {len(entitlements)} entitlements"),
    }


def _retrieval_node(state: WorkflowState) -> WorkflowState:
    query = f"{state['intent']} {state['payload'].get('content') or ''}"
    products = _product_evidence(state["payload"], query)
    docs, backend = retrieve_handbook(query, state["intent"])
    return {
        "products": products,
        "docs": docs,
        "retrieval_backend": backend,
        "trace": _with_trace(state, "Handbook Retriever", f"{len(docs)} handbook chunks via {backend}"),
    }


def _planner_node(state: WorkflowState) -> WorkflowState:
    actions = build_internal_actions(state["intent"], state["strategy"], state["campaigns"], state["entitlements"])
    return {"actions": actions, "trace": _with_trace(state, "Reply Planner", actions)}


def _risk_node(state: WorkflowState) -> WorkflowState:
    flags = build_risk_flags(state["intent"], state["payload"].get("content") or "", state["products"], state["docs"], state["entitlements"])
    sufficiency = evidence_sufficiency(state["intent"], state["products"], state["docs"], flags)
    return {"flags": flags, "sufficiency": sufficiency, "trace": _with_trace(state, "Risk Checker", {"evidence_sufficiency": sufficiency, "flags": flags})}


def _composer_node(state: WorkflowState) -> WorkflowState:
    draft = compose_reply(state["intent"], state["profile"], state["products"], state["campaigns"], state["entitlements"])
    return {"draft": draft, "trace": _with_trace(state, "Final Composer", "reply drafted")}


def _run_nodes_sequentially(payload: dict[str, Any]) -> tuple[WorkflowState, str]:
    state: WorkflowState = {"payload": payload, "trace": []}
    for node in [_intent_node, _profile_node, _resolver_node, _retrieval_node, _planner_node, _risk_node, _composer_node]:
        state.update(node(state))
    return state, "sequential-fallback"


def _run_nodes(payload: dict[str, Any]) -> tuple[WorkflowState, str]:
    try:
        from langgraph.graph import END, START, StateGraph
    except ImportError:
        return _run_nodes_sequentially(payload)
    builder = StateGraph(WorkflowState)
    nodes = [
        ("intent_classifier", _intent_node),
        ("customer_strategy_profiler", _profile_node),
        ("offer_entitlement_resolver", _resolver_node),
        ("handbook_retriever", _retrieval_node),
        ("reply_planner", _planner_node),
        ("risk_checker", _risk_node),
        ("final_composer", _composer_node),
    ]
    for name, node in nodes:
        builder.add_node(name, node)
    builder.add_edge(START, nodes[0][0])
    for current, following in zip(nodes, nodes[1:]):
        builder.add_edge(current[0], following[0])
    builder.add_edge(nodes[-1][0], END)
    return builder.compile().invoke({"payload": payload, "trace": []}), "langgraph"


def run_agent_workflow(payload: dict[str, Any]) -> AgentResult:
    state, orchestration_backend = _run_nodes(payload)
    all_evidence = [*state["products"], *state["docs"]]
    docs = [
        {
            "doc_id": item.id,
            "title": item.title,
            "evidence_id": item.id,
            "evidence_type": item.evidence_type,
            "source_url": item.metadata.get("source_url"),
            "score": item.score,
            "text": item.text,
        }
        for item in state["docs"]
    ]
    return AgentResult(
        intent=state["intent"],
        customer_strategy=state["strategy"],
        customer_profile=state["profile"],
        available_campaigns=state["campaigns"],
        service_entitlements=state["entitlements"],
        retrieved_docs=docs,
        retrieved_evidence=[asdict(item) for item in all_evidence],
        internal_actions=state["actions"],
        reply_plan=state["actions"],
        reply_draft=state["draft"],
        risk_flags=state["flags"],
        risk_warnings=[item["message"] for item in state["flags"]],
        evidence_sufficiency=state["sufficiency"],
        trace=state["trace"],
        orchestration_backend=orchestration_backend,
        retrieval_backend=state["retrieval_backend"],
    )

