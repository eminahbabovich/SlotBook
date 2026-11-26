-- Od push obavijesti (V45) podsjetnik pravi red za mail i po red za svaki browser, sve s istim booking_id.
-- Indeks iz V25 je dozvoljavao jedan red po rezervaciji, pa je push red rušio cijeli krug podsjetnika (23505)
-- čim je neko imao uključene obavijesti. Jedan podsjetnik po rezervaciji sad čuva mail red, a push redova
-- ima koliko i browsera.
DROP INDEX notifications_jedan_podsjetnik;
CREATE UNIQUE INDEX notifications_jedan_podsjetnik ON notifications (booking_id)
    WHERE type = 'BOOKING_REMINDER' AND channel = 'EMAIL';
