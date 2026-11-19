-- Pogrešne lozinke po emailu iz zahtjeva, ne po nalogu: nema stranog ključa na users, pa se nepoznat
-- email broji i zaključa isto kao postojeći i po odgovoru se ne vidi ko ima nalog.
CREATE TABLE login_failures (
    email        VARCHAR(254) PRIMARY KEY,
    failures     INT          NOT NULL,
    -- Kad je počeo prozor od 15 minuta u kojem se greške broje. Prazno dok grešaka nema.
    window_start TIMESTAMPTZ,
    locked_until TIMESTAMPTZ,

    CONSTRAINT login_failures_broj CHECK (failures >= 0),
    CONSTRAINT login_failures_prozor CHECK ((failures = 0) = (window_start IS NULL))
);
