-- Pozivnica ide na email koji možda još nema nalog, pa obavijest tada nema user_id nego adresu.
-- Samo pozivnica ide na adresu, sve ostalo ide korisniku.
ALTER TABLE notifications ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE notifications ADD COLUMN email VARCHAR(254);

ALTER TABLE notifications DROP CONSTRAINT notifications_type;
ALTER TABLE notifications ADD CONSTRAINT notifications_type CHECK (type IN ('BOOKING_AWAITS_CONFIRMATION',
    'BOOKING_CONFIRMED', 'BOOKING_REJECTED', 'BOOKING_CANCELLED', 'BOOKING_EXPIRED', 'EMAIL_VERIFICATION',
    'PASSWORD_RESET', 'ORGANIZATION_INVITE'));

ALTER TABLE notifications ADD CONSTRAINT notifications_primalac CHECK (
    (user_id IS NULL) = (type = 'ORGANIZATION_INVITE')
    AND (email IS NULL) = (type <> 'ORGANIZATION_INVITE'));
