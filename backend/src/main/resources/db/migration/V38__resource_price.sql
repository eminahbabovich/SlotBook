-- Cijena termina u feninzima (1 KM = 100). Stripe isto računa iznos u najmanjoj jedinici valute,
-- pa nema decimala ni zaokruživanja. 0 je besplatan resurs, kao do sada.
ALTER TABLE resources ADD COLUMN price_cents INTEGER NOT NULL DEFAULT 0;
ALTER TABLE resources ADD CONSTRAINT resources_cijena CHECK (price_cents BETWEEN 0 AND 100000);
