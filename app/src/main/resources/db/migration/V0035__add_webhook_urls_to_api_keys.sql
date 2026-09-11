-- FR-9: "configure a webhook URL and register for the webhook API". Keyed by
-- the existing api_keys row (one active key pair per business), so there's
-- no ambiguity about which key a URL belongs to. Both nullable - either can
-- be configured before the other, or left unset.
ALTER TABLE api_keys
    ADD COLUMN callback_url VARCHAR(512),
    ADD COLUMN webhook_url  VARCHAR(512);
