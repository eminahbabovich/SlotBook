ALTER TABLE notifications DROP CONSTRAINT notifications_type;
ALTER TABLE notifications ADD CONSTRAINT notifications_type CHECK (type IN ('BOOKING_AWAITS_CONFIRMATION',
    'BOOKING_CONFIRMED', 'BOOKING_REJECTED', 'BOOKING_CANCELLED', 'BOOKING_EXPIRED', 'BOOKING_REMINDER',
    'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'ORGANIZATION_INVITE'));

-- Najviše jedan podsjetnik po rezervaciji, i kad posao krene dvaput (dvije instance, ponovljen krug).
-- Indeks služi i upitu posla, koji traži rezervacije bez podsjetnika.
CREATE UNIQUE INDEX notifications_jedan_podsjetnik ON notifications (booking_id) WHERE type = 'BOOKING_REMINDER';
