-- Ključ koji klijent šalje uz rezervaciju, da ponovljen zahtjev (dupli klik, loša mreža) ne napravi
-- drugu rezervaciju. Jedinstven je po klijentu: dva studenta smiju slučajno poslati isti ključ.
-- Rezervacije bez ključa ne smetaju jedna drugoj, jer UNIQUE ne poredi NULL vrijednosti.
ALTER TABLE bookings ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE bookings ADD CONSTRAINT bookings_idempotency_key UNIQUE (client_id, idempotency_key);
