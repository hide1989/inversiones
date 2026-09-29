# ADR-001: PostgreSQL with JPA/Hibernate instead of MongoDB

## Status

Accepted

## Context

The initial requirement asked for both MongoDB and JPA as the ORM. These are incompatible: JPA is a
standard for relational databases and there is no viable implementation for MongoDB (Hibernate OGM is
abandoned).

## Decision

Use **PostgreSQL + JPA/Hibernate**. Portfolio data is inherently relational (transactions that belong
to instruments and brokers, time-series exchange rates, weighted average cost calculations and P&L
aggregations). The scaffold's `jpa` driven adapter is first-class, and Testcontainers allows testing
against a real Postgres instance.

## Consequences

- The domain and persistence model must fit a relational schema; document-oriented flexibility is
  traded for referential integrity and transactional guarantees.
- Schema migrations (Flyway) become part of the delivery pipeline.
- Testcontainers-based integration tests require Docker in CI and locally.
