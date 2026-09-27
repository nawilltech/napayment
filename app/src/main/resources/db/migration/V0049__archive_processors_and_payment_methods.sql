-- Soft delete ("archive") for processors and payment methods: nothing is
-- ever hard-deleted. Archived rows are hidden from lists and can't be used
-- for new payments (archiving also deactivates), but stay referenced by
-- history and can be restored. A dedicated column rather than BaseEntity's
-- deleted_at, which is filtered out of every query - archived items must
-- still resolve from past transactions and appear in the "archived" view.
ALTER TABLE payment_processors ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE payment_methods ADD COLUMN archived_at TIMESTAMPTZ;
