# ADR-004: n8n stays as the ingestion layer

## Status

Accepted

## Context

The 20 existing n8n workflows work, have caching via `staticData`, and handle rate limits for eight
external APIs. Reimplementing them in the backend would duplicate work with no benefit.

## Decision

Keep the 20 workflows in n8n. The backend consumes them via a `rest-consumer` driven adapter and adds
what n8n does not provide: a tested domain model, persistence, historical data, and a single security
boundary.

## Consequences

- The backend has a runtime dependency on n8n's availability for data ingestion.
- Data freshness and rate-limit handling remain n8n's responsibility; the backend trusts its output.
- Any change to a workflow's output shape requires coordinating the `rest-consumer` adapter's contract.
