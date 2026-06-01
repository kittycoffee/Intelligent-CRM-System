from __future__ import annotations

from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

from agent_core import run_agent_workflow


class WorkOrderRequest(BaseModel):
    interactionId: int | None = None
    content: str
    interactionType: str | None = None
    customer: dict[str, Any] = Field(default_factory=dict)
    rfm: dict[str, Any] = Field(default_factory=dict)
    products: list[dict[str, Any]] = Field(default_factory=list)
    orders: list[dict[str, Any]] = Field(default_factory=list)
    history: list[dict[str, Any]] = Field(default_factory=list)


app = FastAPI(title="CRM AI Customer Operations Agent", version="1.0.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/agent/work-order")
def agent_work_order(request: WorkOrderRequest) -> dict[str, Any]:
    result = run_agent_workflow(request.model_dump())
    return result.__dict__

