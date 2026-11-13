CREATE TABLE resources (
    id              UUID PRIMARY KEY,
    organization_id UUID          NOT NULL,
    -- Ko vodi resurs: trener, profesor na konsultacijama, osoba zadužena za čitaonicu
    provider_id     UUID          NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    description     VARCHAR(1000),
    slot_minutes    INTEGER       NOT NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL,

    -- Provider mora biti član baš ove organizacije. FK samo na users bi pustio i člana druge
    -- organizacije. Ovaj ključ ujedno znači da organizacija postoji, pa poseban FK ne treba.
    CONSTRAINT resources_provider_clan FOREIGN KEY (organization_id, provider_id)
        REFERENCES organization_members (organization_id, user_id),
    CONSTRAINT resources_slot_minutes CHECK (slot_minutes BETWEEN 5 AND 480)
);

-- Lista resursa organizacije i "moji resursi" za providera. Prva kolona sama pokriva prvi upit.
CREATE INDEX resources_organization_provider ON resources (organization_id, provider_id);
