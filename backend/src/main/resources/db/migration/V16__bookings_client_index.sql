-- "Moje rezervacije": sve rezervacije jednog klijenta, poredane po početku termina
CREATE INDEX bookings_client_start ON bookings (client_id, start_time);
