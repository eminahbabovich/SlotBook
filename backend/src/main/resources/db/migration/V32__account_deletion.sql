-- Obrisan nalog ostaje kao red, da rezervacije, historija i statistika ostanu tačne. Ograničenje
-- čuva da u tom redu nema ničega što vodi do osobe, i kad ga neko mijenja ručno.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE users ADD CONSTRAINT users_obrisan_anoniman CHECK (deleted_at IS NULL OR (
    email = 'obrisan-' || id || '@slotbook.invalid'
    AND display_name = 'Obrisan nalog'
    AND password_hash = '!'
    AND email_verified_at IS NULL));

ALTER TABLE audit_log DROP CONSTRAINT audit_log_radnja;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_radnja CHECK (action IN ('ORGANIZATION_CREATED', 'INVITE_SENT',
    'INVITE_ACCEPTED', 'RESOURCE_CREATED', 'RESOURCE_UPDATED', 'RESOURCE_PROVIDER_CHANGED', 'RESOURCE_DEACTIVATED',
    'ACCOUNT_DELETED'));
