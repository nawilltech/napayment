-- FR-Auth-2: a second factor scoped to moving money. pin_hash mirrors
-- password_hash in every respect (same PasswordEncoder, never reversible,
-- never logged) - both nullable since existing users have neither set at
-- migration time, and pin_hash specifically stays null until the user
-- opts in via POST /api/v1/auth/transaction-pin.
ALTER TABLE users
    ADD COLUMN pin_hash   VARCHAR(255),
    ADD COLUMN pin_set_at TIMESTAMPTZ;
