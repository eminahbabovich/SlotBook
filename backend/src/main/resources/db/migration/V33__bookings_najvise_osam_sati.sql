-- Termin traje najviše 480 minuta (resources_slot_minutes), pa ni rezervacija ne može biti duža.
-- Raspored providera na tome gradi donju granicu za start_time: bez nje je upit kroz indeks
-- bookings_resource_start čitao svu historiju resursa prije traženog dana. Mjerenje je u docs/PERFORMANSE.md.
ALTER TABLE bookings ADD CONSTRAINT bookings_najvise_osam_sati CHECK (end_time - start_time <= INTERVAL '8 hours');
