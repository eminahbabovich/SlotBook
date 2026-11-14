package ba.slotbook.booking;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BookingEventRepository extends JpaRepository<BookingEvent, UUID> {

	// left: istek nema aktera, a običan join bi taj red izbacio iz historije
	@Query("select e from BookingEvent e left join fetch e.actor where e.booking.id = :bookingId order by e.createdAt")
	List<BookingEvent> findHistory(UUID bookingId);

}
