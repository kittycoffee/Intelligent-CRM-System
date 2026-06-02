# AI Customer Operations Agent Service

FastAPI service for evidence-bound CRM work-order assistance.

## Run

```bash
python -m pip install -r requirements.txt
python ingest_handbook.py
uvicorn app:app --reload --port 8090
```

Configuration:

```properties
DASHSCOPE_API_KEY=your_api_key
QWEN_API_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
QWEN_MODEL=qwen3.6-plus
QWEN_API_TIMEOUT_MS=30000
AI_RETRIEVAL_BACKEND=hybrid
QDRANT_LOCAL_PATH=./data/qdrant
QDRANT_EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
```

Hybrid retrieval uses keyword matches first and persisted Qdrant semantic scores as a supplement. MySQL campaigns, service entitlements, product stock, and discount amounts arrive from the Java CRM payload and never use vector search.

The reply composer uses Qwen JSON Mode with `enable_thinking=false`. When Qwen is unavailable or returns invalid structured output, the service falls back to the existing scenario template and reports `generation_backend=template_fallback`.

`POST /agent/work-order` returns:

- detected intent and customer strategy
- eligible campaigns and service entitlements with MySQL evidence IDs
- retrieved handbook chunks with keyword or Qdrant evidence IDs
- internal staff actions and a separate customer-facing reply draft
- risk flags, evidence sufficiency, and node execution trace
- matched products, selected campaigns, policy guidance, used evidence IDs, and generation backend

## Test

```bash
python -m unittest discover -s tests -v
python run_evaluation.py
```
