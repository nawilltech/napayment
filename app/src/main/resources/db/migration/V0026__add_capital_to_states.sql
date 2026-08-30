-- Only meaningful for level = 1 (State) rows; NULL for LGA/Ward rows.
ALTER TABLE states ADD COLUMN capital VARCHAR(128);
