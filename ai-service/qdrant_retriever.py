from __future__ import annotations

import os
from typing import Any


def semantic_scores(query: str, docs: list[dict[str, Any]]) -> tuple[dict[str, float], str]:
    if os.getenv("AI_RETRIEVAL_BACKEND", "lexical").lower() != "qdrant":
        return {}, "lexical"

    try:
        from qdrant_client import QdrantClient, models

        model_name = os.getenv("QDRANT_EMBEDDING_MODEL", "BAAI/bge-small-en")
        client = QdrantClient(":memory:")
        collection_name = "crm_work_order_knowledge"
        client.create_collection(
            collection_name=collection_name,
            vectors_config=models.VectorParams(
                size=client.get_embedding_size(model_name),
                distance=models.Distance.COSINE,
            ),
        )
        texts = [f"{doc['title']} {doc['text']}" for doc in docs]
        client.upload_collection(
            collection_name=collection_name,
            vectors=[models.Document(text=text, model=model_name) for text in texts],
            payload=[{"doc_id": doc["id"]} for doc in docs],
            ids=list(range(len(docs))),
        )
        points = client.query_points(
            collection_name=collection_name,
            query=models.Document(text=query, model=model_name),
            limit=len(docs),
        ).points
        return {
            str(point.payload["doc_id"]): round(float(point.score), 4)
            for point in points
            if point.payload and float(point.score) >= 0.45
        }, "qdrant"
    except Exception:
        return {}, "lexical-fallback"
