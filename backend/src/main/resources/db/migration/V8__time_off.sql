-- Izuzetak od radnog vremena: godišnji, praznik, servis opreme. Za razliku od radnog vremena,
-- ovo je konkretan period, pa su to trenuci (TIMESTAMPTZ), a ne lokalno vrijeme.
CREATE TABLE time_off (
    id          UUID         PRIMARY KEY,
    resource_id UUID         NOT NULL REFERENCES resources (id),
    starts_at   TIMESTAMPTZ  NOT NULL,
    ends_at     TIMESTAMPTZ  NOT NULL,
    -- Vide ga samo admin i provider, klijent vidi samo da termina nema
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT time_off_pocetak_prije_kraja CHECK (starts_at < ends_at)
);

-- Lista budućih izuzetaka (ends_at poslije sada) i, u koraku 5, izuzeci koji padaju u traženi period
CREATE INDEX time_off_resource_ends_at ON time_off (resource_id, ends_at);
