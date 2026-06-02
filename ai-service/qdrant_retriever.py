from __future__ import annotations

import os
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
COLLECTION_NAME = "crm_handbook_chunks"
DEFAULT_MODEL = "BAAI/bge-small-zh-v1.5"


def _client():
    from qdrant_client import QdrantClient

    url = os.getenv("QDRANT_URL", "").strip()
    if url:
        return QdrantClient(url=url)
    path = Path(os.getenv("QDRANT_LOCAL_PATH", str(ROOT / "data" / "qdrant")))
    path.mkdir(parents=True, exist_ok=True)
    return QdrantClient(path=str(path))


def semantic_scores(query: str, docs: list[dict[str, Any]]) -> tuple[dict[str, float], str]:
    """Query the persisted handbook collection. Structured facts never enter Qdrant."""
    if os.getenv("AI_RETRIEVAL_BACKEND", "hybrid").lower() not in {"hybrid", "qdrant"}:
        return {}, "lexical"

    try:
        from qdrant_client import models

        client = _client()
        try:
            if not client.collection_exists(COLLECTION_NAME):
                return {}, "lexical-fallback:not-indexed"
            model_name = os.getenv("QDRANT_EMBEDDING_MODEL", DEFAULT_MODEL)
            points = client.query_points(
                collection_name=COLLECTION_NAME,
                query=models.Document(text=query, model=model_name),
                limit=max(10, len(docs)),
                with_payload=True,
            ).points
            return {
                str(point.payload["doc_id"]): round(float(point.score), 4)
                for point in points
                if point.payload and point.payload.get("doc_id") and float(point.score) >= 0.38
            }, "hybrid:qdrant"
        finally:
            client.close()
    except Exception as exc:
        return {}, f"lexical-fallback:{type(exc).__name__}"


def rebuild_collection(docs: list[dict[str, Any]]) -> int:
    """Rebuild the local handbook index explicitly instead of indexing during requests."""
    from qdrant_client import models

    client = _client()
    model_name = os.getenv("QDRANT_EMBEDDING_MODEL", DEFAULT_MODEL)
    if client.collection_exists(COLLECTION_NAME):
        client.delete_collection(COLLECTION_NAME)
    client.create_collection(
        collection_name=COLLECTION_NAME,
        vectors_config=models.VectorParams(
            size=client.get_embedding_size(model_name),
            distance=models.Distance.COSINE,
        ),
    )
    client.upload_collection(
        collection_name=COLLECTION_NAME,
        vectors=[
            models.Document(text=f"{doc['title']} {doc['text']}", model=model_name)
            for doc in docs
        ],
        payload=[
            {
                "doc_id": doc["id"],
                "title": doc["title"],
                "source_url": doc.get("source_url"),
                "intent_tags": doc.get("intent_tags", []),
            }
            for doc in docs
        ],
        ids=list(range(1, len(docs) + 1)),
    )
    return len(docs)
