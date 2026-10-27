CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    owner_subject VARCHAR(255) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    kind VARCHAR(7) NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE')),
    category VARCHAR(60) NOT NULL CHECK (length(trim(category)) > 0),
    occurred_on DATE NOT NULL,
    note VARCHAR(1000) NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0)
);
CREATE INDEX transactions_owner_month ON transactions (owner_subject, occurred_on DESC, id DESC);

CREATE TABLE budgets (
    owner_subject VARCHAR(255) NOT NULL,
    month VARCHAR(7) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    PRIMARY KEY (owner_subject, month)
);

-- Kept after transaction edits/deletions: a retry returns the ORIGINAL create response.
-- Reservation, transaction insertion, and response snapshot commit atomically.
CREATE TABLE idempotency_requests (
    owner_subject VARCHAR(255) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_body TEXT NOT NULL,
    PRIMARY KEY (owner_subject, request_key)
);
