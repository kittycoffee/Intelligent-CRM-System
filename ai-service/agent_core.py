from __future__ import annotations

import json
import math
import os
import re
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, TypedDict

from qdrant_retriever import semantic_scores


ROOT = Path(__file__).resolve().parent


@dataclass
class Evidence:
    id: str
    source: str
    title: str
    text: str
    score: float
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class AgentResult:
    intent: str
    customer_profile: dict[str, Any]
    retrieved_evidence: list[dict[str, Any]]
    reply_plan: list[str]
    risk_warnings: list[str]
    reply_draft: str
    confidence: float
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
    en_tokens = set(re.findall(r"[a-zA-Z0-9]+", text))
    return zh_tokens | en_tokens


def _score(query: str, doc: str) -> float:
    q = _tokens(query)
    d = _tokens(doc)
    if not q or not d:
        return 0.0
    overlap = len(q & d)
    return overlap / math.sqrt(len(q) * len(d))


def load_static_knowledge() -> list[dict[str, Any]]:
    path = ROOT / "data" / "knowledge_base.json"
    return json.loads(path.read_text(encoding="utf-8"))


def classify_intent(content: str, interaction_type: str | None = None) -> tuple[str, float]:
    text = _normalize(f"{interaction_type or ''} {content}")
    rules = [
        ("complaint", ["投诉", "不满", "差", "生气", "没发货", "太慢", "坏", "退款"]),
        ("after_sales", ["退货", "换货", "售后", "保修", "质量", "包装", "发票"]),
        ("marketing", ["优惠", "活动", "折扣", "券", "双十一", "促销"]),
        ("product_consulting", ["推荐", "买", "有没有", "适合", "商品", "价格", "库存"]),
    ]
    for intent, keywords in rules:
        if any(k in text for k in keywords):
            return intent, 0.86
    return "general_service", 0.62


def build_customer_profile(payload: dict[str, Any]) -> dict[str, Any]:
    customer = payload.get("customer") or {}
    rfm = payload.get("rfm") or {}
    orders = payload.get("orders") or []
    history = payload.get("history") or []
    level = rfm.get("customerLevel") or rfm.get("customer_level") or "未知等级"
    is_vip = "重要" in str(level) or (rfm.get("mScore") or rfm.get("m_score") or 0) >= 4
    return {
        "custId": customer.get("custId") or customer.get("cust_id"),
        "name": customer.get("custName") or customer.get("name") or "客户",
        "gender": customer.get("gender") or "未知",
        "level": level,
        "isVip": bool(is_vip),
        "rfm": rfm,
        "recentOrderCount": len(orders),
        "recentInteractionCount": len(history),
    }


def build_documents(payload: dict[str, Any]) -> list[dict[str, Any]]:
    docs: list[dict[str, Any]] = []
    for p in payload.get("products") or []:
        if p.get("status", 1) != 1:
            continue
        name = p.get("productName") or p.get("name") or "未命名商品"
        features = p.get("features") or ""
        docs.append(
            {
                "id": f"product_{p.get('productId') or name}",
                "source": "product",
                "title": name,
                "text": f"{name} {p.get('category') or ''} {features} 价格 {p.get('price') or ''} 库存 {p.get('stock') or ''}",
                "metadata": p,
            }
        )
    for item in load_static_knowledge():
        docs.append(
            {
                "id": item["id"],
                "source": item["type"],
                "title": item["title"],
                "text": item["text"],
                "metadata": item,
            }
        )
    return docs


def retrieve_evidence(payload: dict[str, Any], intent: str, top_k: int = 5) -> tuple[list[Evidence], str]:
    query = f"{intent} {payload.get('content') or ''}"
    docs = build_documents(payload)
    dense_scores, backend = semantic_scores(query, docs)
    scored: list[Evidence] = []
    for doc in docs:
        lexical_score = _score(query, f"{doc['title']} {doc['text']}")
        s = max(lexical_score, dense_scores.get(doc["id"], 0.0))
        if intent == "product_consulting" and doc["source"] == "product" and lexical_score > 0:
            s += 0.08
        if intent in {"complaint", "after_sales"} and doc["source"] in {"policy", "faq"}:
            s += 0.08
        if s >= 0.04:
            scored.append(
                Evidence(
                    id=doc["id"],
                    source=doc["source"],
                    title=doc["title"],
                    text=doc["text"],
                    score=round(s, 4),
                    metadata=doc.get("metadata") or {},
                )
            )
    scored.sort(key=lambda e: e.score, reverse=True)
    return scored[:top_k], backend


def plan_reply(intent: str, profile: dict[str, Any], evidence: list[Evidence]) -> list[str]:
    steps = ["先确认客户诉求和情绪", "引用可验证的业务数据或政策", "给出下一步处理动作"]
    if profile.get("isVip"):
        steps.insert(1, "使用高价值客户服务策略，语气更主动")
    if intent == "product_consulting":
        if any(e.source == "product" for e in evidence):
            steps.append("只推荐命中的在售商品，不补充未检索到的商品")
        else:
            steps.append("无商品证据时不编造，转为收集需求")
    elif intent == "complaint":
        steps.append("表达歉意并承诺核查，不直接承诺赔付")
    elif intent == "after_sales":
        steps.append("说明售后规则并补充需要客户确认的信息")
    return steps


def check_risks(intent: str, evidence: list[Evidence], draft: str | None = None) -> list[str]:
    warnings: list[str] = []
    product_evidence = [e for e in evidence if e.source == "product"]
    if intent == "product_consulting" and not product_evidence:
        warnings.append("未检索到可推荐的在售商品，禁止编造商品名称或库存。")
    if not evidence:
        warnings.append("没有可引用证据，建议只做通用回复并转人工确认。")
    if draft and any(word in draft for word in ["一定赔偿", "保证退款", "免费赠送"]):
        warnings.append("话术存在越权承诺风险，需要人工确认。")
    return warnings


def _template_reply(intent: str, profile: dict[str, Any], evidence: list[Evidence], warnings: list[str]) -> str:
    name = profile.get("name") or "您好"
    prefix = f"{name}您好，"
    if intent == "product_consulting":
        product = next((e for e in evidence if e.source == "product"), None)
        if product:
            price = product.metadata.get("price")
            features = product.metadata.get("features") or product.text
            return f"{prefix}根据您的需求，目前可以优先看「{product.title}」。这款商品的主要特点是{features}，当前记录价格为{price}。如果您还有预算或使用场景要求，我可以继续帮您缩小范围。"
        return f"{prefix}我暂时没有在系统中查到与您需求完全匹配的在售商品。为了避免推荐不准确，建议您补充预算、使用场景和偏好，我再帮您确认。"
    if intent == "complaint":
        vip_tail = "我会优先帮您跟进，并同步处理进展。" if profile.get("isVip") else "我会帮您记录并推动处理。"
        return f"{prefix}非常抱歉给您带来不好的体验。我先为您核查订单和处理记录，确认原因后给出明确的处理方案。{vip_tail}"
    if intent == "after_sales":
        return f"{prefix}您这个情况可以先按售后流程核对。请您提供订单号、商品状态和包装是否完整，我会根据系统政策帮您确认退换货方案。"
    if intent == "marketing":
        return f"{prefix}我会先核对当前可用活动和您的客户等级，确认有真实优惠后再为您推荐，避免给您不准确的信息。"
    return f"{prefix}您的问题我已经记录，会结合客户信息和历史记录进行核查，并给您一个可执行的处理建议。"


def _call_deepseek(prompt: str) -> str | None:
    api_key = os.getenv("DEEPSEEK_API_KEY")
    if not api_key:
        return None
    url = os.getenv("DEEPSEEK_API_URL", "https://api.deepseek.com/chat/completions")
    model = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    body = json.dumps(
        {
            "model": model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.3,
        },
        ensure_ascii=False,
    ).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            data = json.loads(response.read().decode("utf-8"))
        return data["choices"][0]["message"]["content"].strip()
    except (urllib.error.URLError, KeyError, IndexError, TimeoutError, json.JSONDecodeError):
        return None


def compose_reply(
    payload: dict[str, Any],
    intent: str,
    profile: dict[str, Any],
    evidence: list[Evidence],
    plan: list[str],
    warnings: list[str],
) -> str:
    evidence_text = "\n".join(f"- [{e.source}] {e.title}: {e.text}" for e in evidence[:4])
    prompt = f"""你是AI客户运营工单助手。请基于证据生成客服回复，禁止编造未出现的商品、政策或优惠。

客户画像：{json.dumps(profile, ensure_ascii=False)}
客户问题：{payload.get('content') or ''}
识别意图：{intent}
处理计划：{json.dumps(plan, ensure_ascii=False)}
风险提示：{json.dumps(warnings, ensure_ascii=False)}
证据：
{evidence_text or '无'}

输出要求：只输出一段可直接给客服参考的中文回复，不要Markdown。"""
    return _call_deepseek(prompt) or _template_reply(intent, profile, evidence, warnings)


class WorkflowState(TypedDict, total=False):
    payload: dict[str, Any]
    intent: str
    intent_confidence: float
    profile: dict[str, Any]
    evidence: list[Evidence]
    plan: list[str]
    warnings: list[str]
    draft: str
    retrieval_backend: str
    trace: list[dict[str, Any]]


def _with_trace(state: WorkflowState, node: str, detail: Any) -> list[dict[str, Any]]:
    return [*(state.get("trace") or []), {"node": node, "status": "success", "detail": detail}]


def _intent_node(state: WorkflowState) -> WorkflowState:
    payload = state["payload"]
    intent, intent_confidence = classify_intent(payload.get("content") or "", payload.get("interactionType"))
    return {
        "intent": intent,
        "intent_confidence": intent_confidence,
        "trace": _with_trace(state, "Intent Classifier", f"intent={intent}"),
    }


def _profile_node(state: WorkflowState) -> WorkflowState:
    payload = state["payload"]
    profile = build_customer_profile(payload)
    return {"profile": profile, "trace": _with_trace(state, "Customer Profiler", profile)}


def _retrieval_node(state: WorkflowState) -> WorkflowState:
    payload = state["payload"]
    intent = state["intent"]
    evidence, retrieval_backend = retrieve_evidence(payload, intent)
    return {
        "evidence": evidence,
        "retrieval_backend": retrieval_backend,
        "trace": _with_trace(state, "Knowledge Retriever", f"{len(evidence)} evidence items via {retrieval_backend}"),
    }


def _planner_node(state: WorkflowState) -> WorkflowState:
    intent = state["intent"]
    profile = state["profile"]
    evidence = state["evidence"]
    plan = plan_reply(intent, profile, evidence)
    return {"plan": plan, "trace": _with_trace(state, "Reply Planner", plan)}


def _risk_node(state: WorkflowState) -> WorkflowState:
    intent = state["intent"]
    evidence = state["evidence"]
    warnings = check_risks(intent, evidence)
    return {"warnings": warnings, "trace": _with_trace(state, "Risk Checker", warnings or ["no blocking risk"])}


def _composer_node(state: WorkflowState) -> WorkflowState:
    payload = state["payload"]
    intent = state["intent"]
    profile = state["profile"]
    evidence = state["evidence"]
    plan = state["plan"]
    warnings = state["warnings"]
    draft = compose_reply(payload, intent, profile, evidence, plan, warnings)
    warnings = check_risks(intent, evidence, draft)
    return {
        "draft": draft,
        "warnings": warnings,
        "trace": _with_trace(state, "Final Composer", "reply drafted"),
    }


def _run_nodes_sequentially(payload: dict[str, Any]) -> tuple[WorkflowState, str]:
    state: WorkflowState = {"payload": payload, "trace": []}
    for node in [_intent_node, _profile_node, _retrieval_node, _planner_node, _risk_node, _composer_node]:
        state.update(node(state))
    return state, "sequential-fallback"


def _run_nodes(payload: dict[str, Any]) -> tuple[WorkflowState, str]:
    try:
        from langgraph.graph import END, START, StateGraph
    except ImportError:
        return _run_nodes_sequentially(payload)

    builder = StateGraph(WorkflowState)
    builder.add_node("intent_classifier", _intent_node)
    builder.add_node("customer_profiler", _profile_node)
    builder.add_node("knowledge_retriever", _retrieval_node)
    builder.add_node("reply_planner", _planner_node)
    builder.add_node("risk_checker", _risk_node)
    builder.add_node("final_composer", _composer_node)
    builder.add_edge(START, "intent_classifier")
    builder.add_edge("intent_classifier", "customer_profiler")
    builder.add_edge("customer_profiler", "knowledge_retriever")
    builder.add_edge("knowledge_retriever", "reply_planner")
    builder.add_edge("reply_planner", "risk_checker")
    builder.add_edge("risk_checker", "final_composer")
    builder.add_edge("final_composer", END)
    return builder.compile().invoke({"payload": payload, "trace": []}), "langgraph"


def run_agent_workflow(payload: dict[str, Any]) -> AgentResult:
    state, orchestration_backend = _run_nodes(payload)
    intent = state["intent"]
    intent_confidence = state["intent_confidence"]
    profile = state["profile"]
    evidence = state["evidence"]
    plan = state["plan"]
    warnings = state["warnings"]
    draft = state["draft"]
    trace = state["trace"]

    confidence = intent_confidence
    if evidence:
        confidence += min(0.12, evidence[0].score / 4)
    if warnings:
        confidence -= 0.16
    confidence = round(max(0.2, min(0.95, confidence)), 2)

    return AgentResult(
        intent=intent,
        customer_profile=profile,
        retrieved_evidence=[asdict(e) for e in evidence],
        reply_plan=plan,
        risk_warnings=warnings,
        reply_draft=draft,
        confidence=confidence,
        trace=trace,
        orchestration_backend=orchestration_backend,
        retrieval_backend=state["retrieval_backend"],
    )
