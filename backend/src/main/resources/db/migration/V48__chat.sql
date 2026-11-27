-- AI asistent. Sesija je jedan razgovor jednog člana u jednoj organizaciji. Strani ključ ide na članstvo,
-- ne na korisnika: kad član izađe ili obriše nalog, njegovi razgovori nestanu s članstvom.
CREATE TABLE chat_sessions (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    message_count   INT         NOT NULL DEFAULT 0 CHECK (message_count >= 0),
    created_at      TIMESTAMPTZ NOT NULL,
    last_message_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chat_sessions_clan FOREIGN KEY (organization_id, user_id)
        REFERENCES organization_members (organization_id, user_id) ON DELETE CASCADE
);

CREATE INDEX chat_sessions_clan_idx ON chat_sessions (organization_id, user_id);

-- Poruka je JSON tačno kako ga API vraća i prima, jer se poziv alata i njegov rezultat šalju nazad
-- nepromijenjeni. System prompt se ne čuva, dodaje se pri svakom pozivu. seq je redni broj u sesiji,
-- a primarni ključ ne da dvije poruke na isto mjesto.
CREATE TABLE chat_messages (
    session_id UUID        NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    seq        INT         NOT NULL CHECK (seq > 0),
    role       VARCHAR(10) NOT NULL CHECK (role IN ('user', 'assistant', 'tool')),
    content    JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (session_id, seq)
);

-- Potrošeni tokeni po korisniku i danu. Dan je po UTC-u, jer je granica tu da ne potrošimo besplatnu
-- kvotu provajdera, a ne zbog radnog vremena organizacije.
CREATE TABLE chat_usage (
    user_id UUID   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE   NOT NULL,
    tokens  BIGINT NOT NULL CHECK (tokens >= 0),

    PRIMARY KEY (user_id, day)
);

CREATE INDEX chat_usage_dan ON chat_usage (day);
