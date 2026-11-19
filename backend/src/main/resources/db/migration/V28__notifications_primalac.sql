-- Granica mailova na sat čita zadnje mailove jedne vrste za jednog primaoca (NotificationService).
-- Zahtjev za reset lozinke je javan, pa bez indeksa svaki takav zahtjev čita cijelu tabelu obavijesti.
CREATE INDEX notifications_korisnik_vrsta ON notifications (user_id, type, created_at) WHERE user_id IS NOT NULL;
CREATE INDEX notifications_email_vrsta ON notifications (email, type, created_at) WHERE email IS NOT NULL;

-- Kad nova pozivnica zamijeni staru, kaskada iz V24 je brisala i mail koji je već otišao, pa granica
-- nije vidjela da je osoba dobila mail. Sad servis briše samo mail koji još čeka slanje, a poslan
-- ostaje bez pozivnice. Mail koji čeka mora imati pozivnicu: ako servis zaboravi obrisati takav mail,
-- SET NULL padne na ovom uslovu umjesto da kasnije ode link koji vraća 404.
ALTER TABLE notifications DROP CONSTRAINT notifications_invite_id_fkey;
ALTER TABLE notifications ADD CONSTRAINT notifications_invite_id_fkey
    FOREIGN KEY (invite_id) REFERENCES organization_invites (id) ON DELETE SET NULL;

ALTER TABLE notifications DROP CONSTRAINT notifications_primalac;
ALTER TABLE notifications ADD CONSTRAINT notifications_primalac CHECK (
    (user_id IS NULL) = (type = 'ORGANIZATION_INVITE')
    AND (email IS NULL) = (type <> 'ORGANIZATION_INVITE')
    AND (type = 'ORGANIZATION_INVITE' OR invite_id IS NULL)
    AND (type <> 'ORGANIZATION_INVITE' OR status <> 'PENDING' OR invite_id IS NOT NULL));
