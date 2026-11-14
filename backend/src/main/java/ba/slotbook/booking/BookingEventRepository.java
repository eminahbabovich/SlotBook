package ba.slotbook.booking;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookingEventRepository extends JpaRepository<BookingEvent, UUID> {
}
