-- Mail vlasniku kad neko pokuša registraciju s emailom koji već ima potvrđen nalog
ALTER TABLE notifications DROP CONSTRAINT notifications_type;
ALTER TABLE notifications ADD CONSTRAINT notifications_type CHECK (type IN ('BOOKING_AWAITS_CONFIRMATION',
    'BOOKING_CONFIRMED', 'BOOKING_REJECTED', 'BOOKING_CANCELLED', 'BOOKING_EXPIRED', 'BOOKING_REMINDER',
    'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'ORGANIZATION_INVITE', 'ACCOUNT_EXISTS'));
