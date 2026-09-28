-- Payment methods become a managed catalogue (CRUD in the admin console)
-- instead of a fixed list in code. A method's code is its permanent
-- identifier: processors' methods and transactions reference it, so it is
-- never changed after creation. Seeded with the methods that existed as the
-- former PaymentMethod enum, so every existing reference stays valid.
CREATE TABLE payment_methods (
    id             UUID PRIMARY KEY,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    deleted_at     TIMESTAMPTZ,
    code           VARCHAR(32) NOT NULL,
    name           VARCHAR(64) NOT NULL,
    description    VARCHAR(256),
    display_order  INTEGER NOT NULL DEFAULT 100
);

CREATE UNIQUE INDEX idx_payment_methods_code ON payment_methods (code);
CREATE UNIQUE INDEX idx_payment_methods_name ON payment_methods (LOWER(name));

INSERT INTO payment_methods (id, code, name, description, display_order) VALUES
    (gen_random_uuid(), 'TRANSFER', 'Bank transfer', 'Payer transfers from their bank account or app', 10),
    (gen_random_uuid(), 'CARD', 'Card', 'Debit or credit card', 20),
    (gen_random_uuid(), 'USSD', 'USSD', 'Payer dials a bank USSD code', 30),
    (gen_random_uuid(), 'BANK_DEBIT', 'Bank debit', 'Direct debit from a bank account', 40),
    (gen_random_uuid(), 'QR', 'QR code', 'Payer scans a QR code', 50);

-- The catalogue is now the source of truth for method codes: a processor's
-- method or a transaction can't name a method that doesn't exist, and a
-- method still referenced can't be deleted (deactivate it instead).
ALTER TABLE payment_processor_methods
    ADD CONSTRAINT fk_payment_processor_methods_method FOREIGN KEY (method) REFERENCES payment_methods (code);
ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_payment_method FOREIGN KEY (payment_method) REFERENCES payment_methods (code);
