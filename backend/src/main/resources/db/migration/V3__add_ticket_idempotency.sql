-- Idempotent submission (TRI-18). A client may send an Idempotency-Key with POST /api/v1/tickets; a retry with the
-- same key and the same content returns the original ticket instead of creating a second one.
-- request_fingerprint is the SHA-256 (hex) of the submitted subject and body, to tell a retry from a reused key.
-- The unique constraint decides concurrent retries: one insert wins, the others fail with its name and reload the
-- winner. NULLs are distinct in a PostgreSQL unique constraint, so tickets without a key are unaffected.
ALTER TABLE tickets
    ADD COLUMN idempotency_key     VARCHAR(255),
    ADD COLUMN request_fingerprint CHAR(64),
    ADD CONSTRAINT tickets_customer_idempotency_key_uk UNIQUE (customer_id, idempotency_key),
    ADD CONSTRAINT tickets_idempotency_pair_check
        CHECK ((idempotency_key IS NULL) = (request_fingerprint IS NULL));
