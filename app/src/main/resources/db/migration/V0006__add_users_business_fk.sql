ALTER TABLE users
    ADD CONSTRAINT fk_users_business_id FOREIGN KEY (business_id) REFERENCES business (id);
