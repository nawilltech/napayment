-- A business toggles this to have settlement happen automatically, in
-- real time, right after each successful collection (FR-Settle-1), instead
-- of only on an explicit manual settle request.
ALTER TABLE virtual_accounts ADD COLUMN auto_settle BOOLEAN NOT NULL DEFAULT false;
