-- Tekst emaila se pravi kad obavijest nastane, a ne pri slanju. Tako pošiljalac ne mora znati za
-- rezervacije, a mail kaže ono što je bilo tačno u trenutku promjene.
ALTER TABLE notifications ADD COLUMN subject VARCHAR(200);
ALTER TABLE notifications ADD COLUMN body TEXT;

-- Obavijesti od prije ove migracije postoje samo lokalno (V19 nije bila na produkciji), pa dobiju opšti tekst
UPDATE notifications
SET subject = 'SlotBook: promjena rezervacije',
    body = 'Status tvoje rezervacije se promijenio. Pogledaj je u aplikaciji.'
WHERE subject IS NULL;

ALTER TABLE notifications ALTER COLUMN subject SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN body SET NOT NULL;

-- Zašto zadnji pokušaj nije uspio, da se kod FAILED vidi razlog
ALTER TABLE notifications ADD COLUMN last_error VARCHAR(500);
