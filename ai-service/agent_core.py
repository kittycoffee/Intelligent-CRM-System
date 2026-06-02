from __future__ import annotations

import json
import math
import re
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any, TypedDict

from handbook import load_handbook_chunks
from qdrant_retriever import semantic_scores
from qwen_client import QwenClient, QwenUnavailable


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
    generation_backend: str
    used_evidence_ids: list[str]
    matched_products: list[dict[str, Any]]
    selected_campaigns: list[dict[str, Any]]
    policy_guidance: list[dict[str, Any]]
    rewrite_count: int
    sub_intent: str
    intent_source: str


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
        ("product_consulting", ["推荐", "想买", "有没有", "适合", "商品", "价格", "库存", "入手", "现货", "选哪个", "对比", "switch", "游戏机", "耳机", "护肤品"]),
    ]
    for intent, keywords in rules:
        if any(keyword in text for keyword in keywords):
            return intent
    return "general_service"


def route_intent(content: str, override: str | None = None) -> tuple[str, str, str]:
    if override:
        return override, "", "staff_override"
    intent = classify_intent(content)
    if intent != "general_service":
        return intent, "", "rule"
    try:
        result = QwenClient().generate_json(
            "你是客服意图路由器。输出严格 JSON，字段为 intent、sub_intent。"
            "intent 只能是 product_consulting、complaint、after_sales、promotion_inquiry、general_service。",
            json.dumps({"customer_question": content}, ensure_ascii=False),
        )
        qwen_intent = result.get("intent")
        if qwen_intent not in {"product_consulting", "complaint", "after_sales", "promotion_inquiry", "general_service"}:
            raise QwenUnavailable("Qwen returned an unsupported intent")
        return qwen_intent, str(result.get("sub_intent") or ""), "qwen"
    except QwenUnavailable:
        return intent, "", "rule_fallback"


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


def _campaign_matches_context(item: dict[str, Any], payload: dict[str, Any], intent: str) -> bool:
    category = item.get("productCategory") or item.get("product_category")
    if not category:
        return True
    if intent not in {"product_consulting", "promotion_inquiry"}:
        return False
    content = payload.get("content") or ""
    if category in content:
        return True
    return intent == "product_consulting" and any(
        (product.get("category") or "") == category
        and _score(content, f"{product.get('productName') or ''} {product.get('features') or ''} {category}") >= 0.04
        for product in payload.get("products") or []
    )


def _is_active_campaign(item: dict[str, Any], payload: dict[str, Any], strategy: dict[str, str], intent: str) -> bool:
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
        and _campaign_matches_context(item, payload, intent)
    )


def resolve_structured_facts(payload: dict[str, Any], strategy: dict[str, str], intent: str) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    campaigns = []
    for item in payload.get("campaigns") or []:
        if _is_active_campaign(item, payload, strategy, intent):
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


def retrieve_handbook(query: str, intent: str, top_k: int = 3) -> tuple[list[Evidence], str]:
    docs = load_handbook_chunks()
    semantic, backend = semantic_scores(query, docs)
    scored: list[Evidence] = []
    for doc in docs:
        if intent != "general_service" and intent not in doc.get("intent_tags", []):
            continue
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


def build_policy_guidance(docs: list[Evidence]) -> list[dict[str, Any]]:
    return [
        {
            "evidence_id": item.id,
            "title": item.title,
            "known_rule": item.metadata.get("customer_safe_summary") or item.text,
            "missing_checks": item.metadata.get("required_checks") or [],
            "forbidden_promises": item.metadata.get("forbidden_promises") or [],
        }
        for item in docs
    ]


def select_campaigns(payload: dict[str, Any], campaigns: list[dict[str, Any]]) -> list[dict[str, Any]]:
    selected_ids = {str(item) for item in payload.get("selectedCampaignIds") or []}
    return [item for item in campaigns if str(item["campaign_id"]) in selected_ids]


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


def compose_reply(intent: str, profile: dict[str, Any], products: list[Evidence], campaigns: list[dict[str, Any]], entitlements: list[dict[str, Any]], policy_guidance: list[dict[str, Any]] | None = None) -> str:
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
        guidance_text = "".join(item["known_rule"] for item in (policy_guidance or []) if item.get("known_rule"))
        return prefix + (guidance_text or "我们已经记录您的售后诉求。") + "请提供订单号和商品状态，我先为您核实。" + entitlement_text
    if intent == "promotion_inquiry":
        return prefix + (campaign_text or "暂未查询到适用于您的有效活动，我们会继续为您核实。")
    if intent == "product_consulting":
        if not products:
            return prefix + "暂未查询到与您需求匹配的在售商品。为避免推荐不准确，请补充预算、使用场景和偏好。"
        product = products[0]
        price = product.metadata.get("price")
        stock = product.metadata.get("stock")
        suffix = f"根据您的需求，可以优先了解“{product.title}”。该商品当前库存为{stock}件" + (f"，价格为{price}元" if price else "") + f"，主要特点是{product.metadata.get('features') or product.text}。"
        return prefix + suffix + (campaign_text if campaign_text else "")
    return prefix + "您的问题已经记录，我们会结合订单和业务规则核实后给出明确处理建议。"


def _evidence_payload(state: WorkflowState) -> list[dict[str, Any]]:
    evidence = [
        {
            "evidence_id": item.id,
            "source": item.source,
            "title": item.title,
            "text": item.text,
            "intent_tags": item.metadata.get("intent_tags", []),
            "metadata": item.metadata,
        }
        for item in [*state["products"], *state["docs"]]
    ]
    evidence.extend(
        {
            "evidence_id": item["evidence_id"],
            "source": "campaign",
            "title": item["campaign_name"],
            "text": item.get("allowedReplyText") or item.get("allowed_reply_text") or "",
        }
        for item in state["selected_campaigns"]
    )
    evidence.extend(
        {
            "evidence_id": item["evidence_id"],
            "source": "entitlement",
            "title": item["name"],
            "text": item.get("allowedReplyText") or item.get("allowed_reply_text") or "",
        }
        for item in state["entitlements"]
    )
    return evidence


def _grounding_violations(state: WorkflowState, draft: str, actions: list[str], used_ids: list[str]) -> list[str]:
    violations = []
    selected_ids = {item["evidence_id"] for item in state["selected_campaigns"]}
    for item in state["campaigns"]:
        if item["evidence_id"] not in selected_ids and item["campaign_name"] in draft:
            violations.append(f"未选活动不得进入回复：{item['campaign_name']}")
    for promise in ["一定赔偿", "保证退款", "一定到账", "保证到账"]:
        if promise in draft:
            violations.append(f"存在越权承诺：{promise}")
    for item in actions:
        if item and item in draft:
            violations.append("客服内部动作不得原样进入客户回复")
            break
    for item in state["selected_campaigns"]:
        allowed_text = item.get("allowedReplyText") or item.get("allowed_reply_text") or ""
        if item["campaign_name"] not in draft and allowed_text not in draft:
            violations.append(f"选中活动必须自然写入回复：{item['campaign_name']}")
        if item["evidence_id"] not in used_ids:
            violations.append(f"活动缺少证据引用：{item['campaign_name']}")
    return violations


def _validate_qwen_result(state: WorkflowState, result: dict[str, Any], evidence_ids: set[str]) -> tuple[str, list[str], list[str]]:
    draft = result.get("reply_draft")
    actions = result.get("internal_actions")
    used_ids = result.get("used_evidence_ids")
    if not isinstance(draft, str) or not draft.strip():
        raise QwenUnavailable("Qwen reply_draft is empty")
    if not isinstance(actions, list) or not all(isinstance(item, str) for item in actions):
        raise QwenUnavailable("Qwen internal_actions must be a string list")
    if not isinstance(used_ids, list) or not all(isinstance(item, str) and item in evidence_ids for item in used_ids):
        raise QwenUnavailable("Qwen used_evidence_ids contains unknown evidence")
    violations = _grounding_violations(state, draft, actions, used_ids)
    if violations:
        raise QwenUnavailable("；".join(violations))
    return draft.strip(), actions, used_ids


def compose_grounded_reply(state: WorkflowState) -> tuple[str, list[str], list[str], str, int]:
    fallback = compose_reply(state["intent"], state["profile"], state["products"], state["selected_campaigns"], state["entitlements"], state["policy_guidance"])
    evidence = _evidence_payload(state)
    evidence_ids = {item["evidence_id"] for item in evidence}
    system_prompt = (
        "你是电商 CRM 客服回复助手。只能使用输入 JSON 中的证据生成自然、简洁、可直接发送给客户的中文回复。"
        "不得编造商品、库存、价格、物流节点、优惠、退款时限或赔偿承诺。"
        "selected_campaigns 中的活动是客服已确认要推荐的活动，必须自然写入回复并引用对应 evidence_id。"
        "回复必须是纯文本，不得使用 Markdown 标题、列表或加粗符号。"
        "输出严格 JSON 对象，字段为 reply_draft、internal_actions、used_evidence_ids。"
        "internal_actions 仅供客服内部查看，不得混入 reply_draft。"
    )
    user_prompt = json.dumps(
        {
            "customer_question": state["payload"].get("content") or "",
            "intent": state["intent"],
            "customer_profile": state["profile"],
            "evidence_sufficiency": state["sufficiency"],
            "risk_flags": state["flags"],
            "related_order": state["payload"].get("relatedOrder") or {},
            "policy_guidance": state["policy_guidance"],
            "selected_campaigns": state["selected_campaigns"],
            "evidence": evidence,
        },
        ensure_ascii=False,
    )
    try:
        client = QwenClient()
        result = client.generate_json(system_prompt, user_prompt)
        try:
            draft, actions, used_ids = _validate_qwen_result(state, result, evidence_ids)
            return draft, actions, used_ids, "qwen", 0
        except QwenUnavailable as exc:
            rewrite_prompt = user_prompt + "\n上一次输出未通过校验，请修正后重新输出 JSON。违规原因：" + str(exc)
            draft, actions, used_ids = _validate_qwen_result(state, client.generate_json(system_prompt, rewrite_prompt), evidence_ids)
            return draft, actions, used_ids, "qwen_rewrite", 1
    except QwenUnavailable:
        return fallback, state["actions"], [], "template_fallback", 0


class WorkflowState(TypedDict, total=False):
    payload: dict[str, Any]
    intent: str
    sub_intent: str
    intent_source: str
    profile: dict[str, Any]
    strategy: dict[str, str]
    products: list[Evidence]
    docs: list[Evidence]
    campaigns: list[dict[str, Any]]
    selected_campaigns: list[dict[str, Any]]
    entitlements: list[dict[str, Any]]
    policy_guidance: list[dict[str, Any]]
    actions: list[str]
    flags: list[dict[str, str]]
    sufficiency: str
    draft: str
    generation_backend: str
    used_evidence_ids: list[str]
    rewrite_count: int
    retrieval_backend: str
    trace: list[dict[str, Any]]


def _with_trace(state: WorkflowState, node: str, detail: Any) -> list[dict[str, Any]]:
    return [*(state.get("trace") or []), {"node": node, "status": "success", "detail": detail}]


def _intent_node(state: WorkflowState) -> WorkflowState:
    payload = state["payload"]
    intent, sub_intent, intent_source = route_intent(payload.get("content") or "", payload.get("intentOverride"))
    return {
        "intent": intent,
        "sub_intent": sub_intent,
        "intent_source": intent_source,
        "trace": _with_trace(state, "Intent Classifier", f"intent={intent}, source={intent_source}"),
    }


def _profile_node(state: WorkflowState) -> WorkflowState:
    profile = build_customer_profile(state["payload"])
    strategy = {"value_tier": profile["value_tier"], "lifecycle_risk": profile["lifecycle_risk"]}
    return {"profile": profile, "strategy": strategy, "trace": _with_trace(state, "Customer Strategy Profiler", strategy)}


def _resolver_node(state: WorkflowState) -> WorkflowState:
    campaigns, entitlements = resolve_structured_facts(state["payload"], state["strategy"], state["intent"])
    selected_campaigns = select_campaigns(state["payload"], campaigns)
    return {
        "campaigns": campaigns,
        "selected_campaigns": selected_campaigns,
        "entitlements": entitlements,
        "trace": _with_trace(state, "Offer & Entitlement Resolver", f"{len(campaigns)} candidate campaigns, {len(selected_campaigns)} selected, {len(entitlements)} entitlements"),
    }


def _retrieval_node(state: WorkflowState) -> WorkflowState:
    query = f"{state['intent']} {state['payload'].get('content') or ''}"
    products = _product_evidence(state["payload"], query)
    docs, backend = retrieve_handbook(query, state["intent"])
    policy_guidance = build_policy_guidance(docs)
    return {
        "products": products,
        "docs": docs,
        "policy_guidance": policy_guidance,
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
    draft, actions, used_evidence_ids, generation_backend, rewrite_count = compose_grounded_reply(state)
    return {
        "draft": draft,
        "actions": actions,
        "used_evidence_ids": used_evidence_ids,
        "generation_backend": generation_backend,
        "rewrite_count": rewrite_count,
        "trace": _with_trace(state, "Final Composer", f"reply drafted via {generation_backend}"),
    }


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
            "intent_tags": item.metadata.get("intent_tags", []),
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
        generation_backend=state["generation_backend"],
        used_evidence_ids=state["used_evidence_ids"],
        matched_products=[asdict(item) for item in state["products"]],
        selected_campaigns=state["selected_campaigns"],
        policy_guidance=state["policy_guidance"],
        rewrite_count=state["rewrite_count"],
        sub_intent=state["sub_intent"],
        intent_source=state["intent_source"],
    )
