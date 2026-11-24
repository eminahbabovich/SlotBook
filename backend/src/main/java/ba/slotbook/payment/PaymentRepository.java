package ba.slotbook.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository extends JpaRepository<Payment, UUID> {

	// Najviše jedna, to čuva indeks payments_jedna_otvorena
	Optional<Payment> findByBookingIdAndStatus(UUID bookingId, PaymentStatus status);

}
