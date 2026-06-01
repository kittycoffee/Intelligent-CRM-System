# AI Customer Operations Agent Service

This service provides the AI workflow behind the CRM work-order assistant.

It exposes a FastAPI endpoint for the Java CRM backend, while keeping the core
workflow in pure Python so it can be tested without external services.

## Run

```bash
cd ai-service
python -m pip install -r requirements.txt
uvicorn app:app --reload --port 8090
```

Optional environment variables:

```properties
DEEPSEEK_API_KEY=your_key
DEEPSEEK_API_URL=https://api.deepseek.com/chat/completions
DEEPSEEK_MODEL=deepseek-chat
```

If no API key is configured, the workflow falls back to deterministic template
generation. This keeps demos and tests stable.

## API

`POST /agent/work-order`

Input is a CRM work-order context with customer profile, RFM snapshot, products,
orders, and recent interactions. Output contains:

- intent classification
- customer profile summary
- retrieved evidence
- reply plan
- risk warnings
- final reply draft
- confidence score
- node execution trace

## Tests

```bash
python -m unittest discover tests
```
