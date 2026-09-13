-- FR-Auth-1: peer-to-peer transfer reuses the existing transactions ledger
-- (a transfer is still fundamentally a DEBIT on one virtual account and a
-- CREDIT on another - no new TransactionType) rather than a parallel table.
--
-- payment_processor_id becomes nullable: a wallet-to-wallet transfer touches
-- no external processor at all, so there is nothing honest to put there
-- (doc 2 ADR-12 records the reasoning - relaxing the constraint was chosen
-- over seeding a fake "internal" PaymentProcessor row, which would misrepresent
-- a table meant to hold real integrations only).
ALTER TABLE transactions
    ALTER COLUMN payment_processor_id DROP NOT NULL,
    ADD COLUMN transfer_group_id      UUID,
    ADD COLUMN counterparty_account_id UUID;

-- Both rows of one transfer (the sender's DEBIT and the recipient's CREDIT)
-- share one transfer_group_id - this is how a transfer is looked up/displayed
-- as a single event despite living as two ledger rows.
CREATE INDEX idx_transactions_transfer_group_id ON transactions (transfer_group_id);
