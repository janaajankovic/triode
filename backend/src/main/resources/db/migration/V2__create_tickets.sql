-- Tickets with the columns the Ticket aggregate has today. Later stories add their own columns
-- (idempotency_key in TRI-18, AI fields in Sprint 3). version is the optimistic-locking counter.
CREATE TABLE tickets (
    id          UUID          NOT NULL PRIMARY KEY,
    customer_id UUID          NOT NULL,
    subject     VARCHAR(255)  NOT NULL,
    body        VARCHAR(10000) NOT NULL,
    status      VARCHAR(32)   NOT NULL
        CONSTRAINT tickets_status_check CHECK (status IN (
            'NEW', 'TRIAGING', 'NEEDS_MANUAL_TRIAGE', 'PENDING_REVIEW', 'RESOLVED', 'ESCALATED')),
    created_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL
);
