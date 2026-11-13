-- Raspon vremena u danu. Postgres ima tsrange i tstzrange, ali ne i raspon za TIME, pa ga pravimo.
-- Kraj je isključen kao kod ostalih raspona, pa se 08:00-12:00 i 12:00-16:00 ne preklapaju.
CREATE TYPE timerange AS RANGE (subtype = time);

CREATE TABLE availability_rules (
    id          UUID    PRIMARY KEY,
    resource_id UUID    NOT NULL REFERENCES resources (id),
    -- ISO redoslijed kao java.time.DayOfWeek: 1 je ponedjeljak, 7 nedjelja
    day_of_week INTEGER NOT NULL,
    -- Lokalno vrijeme u zoni organizacije, bez datuma i bez zone. U UTC se pretvara tek za
    -- konkretan datum, jer isto 08:00 zimi i ljeti nije isti UTC trenutak.
    start_time  TIME    NOT NULL,
    end_time    TIME    NOT NULL,

    CONSTRAINT availability_rules_dan CHECK (day_of_week BETWEEN 1 AND 7),
    -- Smjena preko ponoći (22:00-02:00) se zato upisuje kao dva intervala, u dva dana
    CONSTRAINT availability_rules_pocetak_prije_kraja CHECK (start_time < end_time),
    -- Dva intervala istog resursa istog dana se ne smiju preklapati. Indeks ovog constrainta
    -- počinje s resource_id, pa pokriva i čitanje pravila jednog resursa.
    CONSTRAINT availability_rules_bez_preklapanja EXCLUDE USING gist (
        resource_id WITH =,
        day_of_week WITH =,
        timerange(start_time, end_time) WITH &&
    )
);
