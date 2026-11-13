package ba.slotbook.availability;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface TimeOffRepository extends JpaRepository<TimeOff, UUID> {

	Page<TimeOff> findByResourceIdAndEndsAtAfter(UUID resourceId, Instant sada, Pageable pageable);

	Optional<TimeOff> findByIdAndResourceId(UUID id, UUID resourceId);

}
