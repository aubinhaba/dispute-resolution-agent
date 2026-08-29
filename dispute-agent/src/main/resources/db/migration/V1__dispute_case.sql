-- dispute_case is the CLAIM: its primary key is the idempotency mechanism.
-- dispute_case_event is the HISTORY: one row per transition, never rewritten.
-- See ADR-0017.

CREATE TABLE dispute_case (
    dispute_id   TEXT        PRIMARY KEY,
    submitted_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE dispute_case_event (
    seq                 BIGSERIAL   PRIMARY KEY,
    dispute_id          TEXT        NOT NULL,
    status              TEXT        NOT NULL,

    decision            TEXT,
    confidence          DOUBLE PRECISION,
    rationale           TEXT,
    cited_reason_code   TEXT,
    -- TEXT[] and not JSONB: these are lists of strings, mapped to String[] without a converter
    cited_rule_passages TEXT[]      NOT NULL DEFAULT '{}',
    evidence_refs       TEXT[]      NOT NULL DEFAULT '{}',
    agent_version       TEXT,
    decided_at          TIMESTAMPTZ,

    failure_reason      TEXT,
    submitted_at        TIMESTAMPTZ NOT NULL,
    -- completed_at is the DOMAIN field (null while PENDING); occurred_at is the AUDIT field.
    -- Merging them would surface a PENDING case carrying an end date
    completed_at        TIMESTAMPTZ,
    occurred_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX dispute_case_event_lookup ON dispute_case_event (dispute_id, seq DESC);

-- Append-only stops being a code convention here: a convention cannot be made to go red
CREATE OR REPLACE FUNCTION dispute_case_event_append_only() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'dispute_case_event is append-only: % refused', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER dispute_case_event_no_rewrite
    BEFORE UPDATE OR DELETE ON dispute_case_event
    FOR EACH ROW EXECUTE FUNCTION dispute_case_event_append_only();
