-- Mail pozivnice pripada pozivnici. Kad nova pozivnica zamijeni staru, stara se briše, a s njom
-- i njen mail koji možda još čeka slanje. Inače bi otišao link koji vraća 404.
ALTER TABLE notifications ADD COLUMN invite_id UUID REFERENCES organization_invites (id) ON DELETE CASCADE;
CREATE INDEX notifications_invite_id ON notifications (invite_id) WHERE invite_id IS NOT NULL;

-- V23 nikad nije bila na produkciji, pa pozivnica bez invite_id ima samo u lokalnoj bazi
DELETE FROM notifications WHERE type = 'ORGANIZATION_INVITE';

ALTER TABLE notifications DROP CONSTRAINT notifications_primalac;
ALTER TABLE notifications ADD CONSTRAINT notifications_primalac CHECK (
    (user_id IS NULL) = (type = 'ORGANIZATION_INVITE')
    AND (email IS NULL) = (type <> 'ORGANIZATION_INVITE')
    AND (invite_id IS NULL) = (type <> 'ORGANIZATION_INVITE'));
