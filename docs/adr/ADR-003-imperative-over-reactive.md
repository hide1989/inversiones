# ADR-003: Imperative style instead of reactive

## Status

Accepted

## Context

The clean architecture scaffold offers `--type=IMPERATIVE` and `--type=REACTIVE`. JPA/Hibernate is
blocking; combining it with WebFlux would block the reactive event loop and negate the benefits of a
reactive stack.

## Decision

Use the **imperative** style with the `restmvc` entry point. With Java 25 virtual threads
(`spring.threads.virtual.enabled=true`), the imperative model scales more than enough for this
workload's expected load.

## Consequences

- Simpler programming model: no reactive operators, backpressure, or Mono/Flux chains to reason about.
- Thread-per-request semantics rely on virtual threads for throughput instead of a reactive runtime.
- If a future requirement needs true reactive streaming (e.g. server-sent events at scale), this
  decision would need to be revisited.
