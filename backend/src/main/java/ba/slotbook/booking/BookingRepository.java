package ba.slotbook.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookingRepository extends JpaRepository<Booking, UUID> {

	// Rezervacije koje se preklapaju s periodom od..do: počinju prije kraja i završavaju poslije početka
	List<Booking> findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(UUID resourceId,
			Collection<BookingStatus> statusi, Instant to, Instant from);

}
