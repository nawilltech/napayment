-- Optional processor logo (shown in the admin console and at checkout):
-- a base64 data URL, e.g. data:image/png;base64,... - PNG, JPEG or WebP,
-- at most 100 KB decoded (validated by ProcessorLogo). NULL = no logo.
ALTER TABLE payment_processors ADD COLUMN logo TEXT;
