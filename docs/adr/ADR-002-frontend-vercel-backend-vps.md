# ADR-002: Frontend on Vercel, backend on the Hostinger VPS

## Status

Accepted

## Context

Vercel Functions support Node.js, Python, Go, Ruby, Rust, Bun, WebAssembly and Edge runtimes. There is
no Java runtime, official or community-maintained
(https://vercel.com/docs/functions/runtimes). The Java backend cannot be deployed to Vercel.

## Decision

Deploy the **frontend on Vercel** (static build, global CDN, free tier) and the **backend plus
PostgreSQL on the existing Hostinger VPS** with Docker, alongside n8n. This adds zero extra cost, avoids
cold starts, and keeps latency to n8n minimal since both run on the same machine.

Alternatives considered and discarded:
- Render free tier: 512 MB RAM and suspension after 15 minutes of inactivity, with a 30-60s cold start.
- Google Cloud Run: generous free tier but requires a GCP account with a credit card and complicates
  the pipeline with `gcloud auth` and Artifact Registry.
- Fly.io: no longer offers a real free tier.

## Consequences

- The backend deployment and the VPS's operational health become coupled; VPS downtime affects the API.
- Frontend and backend live on separate infrastructure providers, requiring CORS configuration and
  environment-specific API base URLs.
- No managed autoscaling for the backend; capacity planning is manual.
