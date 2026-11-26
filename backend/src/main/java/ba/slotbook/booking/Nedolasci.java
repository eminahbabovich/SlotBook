package ba.slotbook.booking;

import java.time.Instant;

// Nedolasci jednog člana u jednoj organizaciji u periodu: koliko ih je i kad je bio zadnji
record Nedolasci(long broj, Instant zadnji) {
}
