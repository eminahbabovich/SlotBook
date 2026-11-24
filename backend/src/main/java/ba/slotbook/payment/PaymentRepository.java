package ba.slotbook.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PaymentRepository extends JpaRepository<Payment, UUID> {

	// Najviše jedna, to čuva indeks payments_jedna_otvorena
	Optional<Payment> findByBookingIdAndStatus(UUID bookingId, PaymentStatus status);

	Optional<Payment> findByStripeSessionId(String stripeSessionId);

	// Samo ID, bez učitavanja uplate. Uplata pročitana prije locka rezervacije bi u memoriji ostala
	// OPEN i kad ju je druga transakcija u međuvremenu platila.
	@Query("select p.booking.id from Payment p where p.stripeSessionId = :sesija")
	Optional<UUID> findBookingIdBySession(String sesija);

}
