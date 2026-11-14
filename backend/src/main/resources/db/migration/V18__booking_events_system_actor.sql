-- Istek i zatvaranje završenih radi pozadinski posao, ne korisnik. Tada actor_id ostaje prazan.
ALTER TABLE booking_events ALTER COLUMN actor_id DROP NOT NULL;

-- Prazan akter samo za ta dva prelaza. Potvrdu, odbijanje i otkazivanje uvijek radi neko.
ALTER TABLE booking_events ADD CONSTRAINT booking_events_akter
    CHECK (actor_id IS NOT NULL OR to_status IN ('EXPIRED', 'COMPLETED'));
