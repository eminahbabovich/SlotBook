-- Čitaonica potvrđuje sama, profesor na konsultacijama hoće prvo vidjeti ko dolazi.
-- Postojeći resursi ostaju na automatskoj potvrdi, kao do sada.
ALTER TABLE resources ADD COLUMN auto_confirm BOOLEAN NOT NULL DEFAULT TRUE;
