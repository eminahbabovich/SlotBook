-- Tajni link za pretplatu na kalendar, jedan po korisniku. Baza čuva samo hash tokena, kao kod
-- refresh tokena: ko pročita bazu, ne može čitati tuđe kalendare. Novi link zamijeni stari.
CREATE TABLE calendar_feeds (
    user_id    UUID        PRIMARY KEY REFERENCES users (id),
    token_hash TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);
