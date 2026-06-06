# Data Contracts And Quality Checks

Use this file to document expected joins, grains, freshness, and validation checks before building reports or dashboards.

## Join Keys

- `customer_id`: joins customers, orders, interactions, RFM scores, and customer-level AI events.
- `order_id`: joins order header, order items, payments, refunds, and fulfillment data.
- `conversation_id`: joins AI Agent messages, tool calls, retrieval events, and model calls.
- `document_id`: joins RAG documents, chunks, embeddings, retrieval events, and citations.

## Required Checks

- Primary keys are unique at the declared grain.
- Foreign key joins do not unexpectedly drop large portions of rows.
- Time columns are stored and analyzed with a clear timezone.
- Metric windows use explicit inclusive and exclusive date boundaries.
- Revenue logic documents treatment of refunds, cancellations, discounts, and test data.
- Agent success metrics use a maintained status mapping.
- RAG metrics distinguish retrieval availability from answer quality.

## Open Decisions

- Confirm actual database schema and migration file locations.
- Confirm whether product analytics events exist in the Vue frontend.
- Confirm AI service telemetry persistence model.
- Decide canonical reporting timezone for leadership-facing reports.

