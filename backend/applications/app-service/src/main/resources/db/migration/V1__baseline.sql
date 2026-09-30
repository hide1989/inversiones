-- Baseline migration. The real schema arrives in task 3 of this plan.
CREATE TABLE IF NOT EXISTS flyway_baseline (
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
