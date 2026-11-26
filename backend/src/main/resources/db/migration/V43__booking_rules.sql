-- Pravila korištenja. Sva su opciona: NULL znači da pravila nema.

-- Resurs: najviše budućih rezervacija po članu, koliko dana unaprijed se rezerviše (po kalendaru u
-- zoni organizacije) i najmanje minuta prije početka
ALTER TABLE resources
    ADD COLUMN max_active_per_member INTEGER,
    ADD COLUMN max_days_ahead        INTEGER,
    ADD COLUMN min_notice_minutes    INTEGER,
    ADD CONSTRAINT resources_najvise_aktivnih CHECK (max_active_per_member BETWEEN 1 AND 100),
    ADD CONSTRAINT resources_dana_unaprijed CHECK (max_days_ahead BETWEEN 1 AND 365),
    ADD CONSTRAINT resources_minuta_prije CHECK (min_notice_minutes BETWEEN 1 AND 10080);

-- Organizacija: toliko nedolazaka u toliko dana daje blokadu od toliko dana. Blokada se ne čuva,
-- računa se iz rezervacija sa statusom NO_SHOW. Tri broja idu zajedno: ili su sva tri ili nijedan.
ALTER TABLE organizations
    ADD COLUMN no_show_limit       INTEGER,
    ADD COLUMN no_show_window_days INTEGER,
    ADD COLUMN no_show_block_days  INTEGER,
    ADD CONSTRAINT organizations_nedolasci CHECK (
        (no_show_limit IS NULL AND no_show_window_days IS NULL AND no_show_block_days IS NULL)
        OR (no_show_limit BETWEEN 1 AND 20 AND no_show_window_days BETWEEN 1 AND 365
            AND no_show_block_days BETWEEN 1 AND 365));

-- Brojanje nedolazaka jednog člana: po klijentu i statusu, pa po vremenu
CREATE INDEX bookings_nedolasci ON bookings (client_id, start_time) WHERE status = 'NO_SHOW';

ALTER TABLE audit_log DROP CONSTRAINT audit_log_radnja;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_radnja CHECK (action IN ('ORGANIZATION_CREATED', 'INVITE_SENT',
    'INVITE_ACCEPTED', 'RESOURCE_CREATED', 'RESOURCE_UPDATED', 'RESOURCE_PROVIDER_CHANGED', 'RESOURCE_DEACTIVATED',
    'ACCOUNT_DELETED', 'INVITE_REVOKED', 'INVITE_LINK_CREATED', 'INVITE_LINK_REVOKED', 'INVITE_LINK_JOINED',
    'MEMBER_ROLE_CHANGED', 'MEMBER_REMOVED', 'ORGANIZATION_RULES_CHANGED'));
