from __future__ import annotations

from handbook import load_handbook_chunks
from qdrant_retriever import COLLECTION_NAME, rebuild_collection


if __name__ == "__main__":
    count = rebuild_collection(load_handbook_chunks())
    print(f"indexed {count} handbook chunks into {COLLECTION_NAME}")

