CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,

    -- Java spušta email u mala slova, ali UNIQUE gore radi samo ako to važi za svaki red,
    -- i za onaj koji neko upiše ručno. Inače bi Amra@example.com i amra@example.com bila dva naloga.
    CONSTRAINT users_email_mala_slova CHECK (email = lower(email))
);
