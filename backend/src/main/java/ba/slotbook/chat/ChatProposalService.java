package ba.slotbook.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.booking.BookingResponse;
import ba.slotbook.booking.BookingService;
import ba.slotbook.booking.BookingStatus;
import ba.slotbook.chat.ChatProposalNotUsableException.Razlog;
import ba.slotbook.chat.ChatProposalRepository.Prijedlog;
import ba.slotbook.resource.ResourceAccess;
import ba.slotbook.resource.ResourceResponse;
import ba.slotbook.resource.ResourceService;

// Model samo upisuje prijedlog. Rezervaciju pravi ili otkazuje korisnik dugmetom Potvrdi, kroz isti
// BookingService kao kalendar, pa vrijede isti 409, ista pravila korištenja i isti mailovi.
@Service
public class ChatProposalService {

	private final ChatProposalRepository prijedlozi;
	private final BookingService bookings;
	private final ResourceService resources;
	private final ResourceAccess resourceAccess;
	private final Clock clock;
	private final Duration trajanje;

	ChatProposalService(ChatProposalRepository prijedlozi, BookingService bookings, ResourceService resources,
			ResourceAccess resourceAccess, Clock clock, @Value("${slotbook.ai.proposal-minutes}") int minuta) {
		this.prijedlozi = prijedlozi;
		this.bookings = bookings;
		this.resources = resources;
		this.resourceAccess = resourceAccess;
		this.clock = clock;
		this.trajanje = Duration.ofMinutes(minuta);
	}

	UUID predloziRezervaciju(UUID sesija, UUID resurs, Instant pocetak) {
		return upisi(sesija, "BOOK", resurs, pocetak, null);
	}

	UUID predloziOtkazivanje(UUID sesija, UUID rezervacija) {
		return upisi(sesija, "CANCEL", null, null, rezervacija);
	}

	long minutaDoIsteka() {
		return trajanje.toMinutes();
	}

	// Lock na prijedlogu drži dvije potvrde u redu. BookingService.create i cancel se pridruže ovoj
	// transakciji, pa se rezervacija i oznaka "potvrđeno" upišu zajedno ili nijedno.
	@Transactional
	public ChatProposalResponse confirm(UUID proposalId, Authentication authentication) {
		Prijedlog prijedlog = prijedlozi.zakljucaj(proposalId);
		boolean otkazivanje = "CANCEL".equals(prijedlog.akcija());
		if (prijedlog.confirmedAt() != null && otkazivanje) {
			// Otkazana rezervacija se ne otkazuje dvaput, a dupli klik nije greška
			return new ChatProposalResponse(prijedlog.id(), prijedlog.akcija(), prijedlog.bookingId(),
					BookingStatus.CANCELLED);
		}
		if (prijedlog.confirmedAt() == null) {
			provjeriDaVazi(prijedlog, authentication);
		}
		// Ponovljena potvrda rezervacije ide opet kroz create s istim ključem, pa dobije prvu rezervaciju
		BookingResponse rezervacija = otkazivanje ? bookings.cancel(prijedlog.bookingId(), prijedlog.userId(), false)
				: bookings.create(prijedlog.resourceId(), prijedlog.userId(), prijedlog.startTime(), prijedlog.kljuc());
		prijedlozi.potvrdi(prijedlog.id(), rezervacija.id(), clock.instant());
		return new ChatProposalResponse(prijedlog.id(), prijedlog.akcija(), rezervacija.id(), rezervacija.status());
	}

	// Ponovljeno odbijanje nije greška. Potvrđen prijedlog se ne odbija, rezervacija se otkazuje kao i svaka.
	@Transactional
	public void dismiss(UUID proposalId) {
		Prijedlog prijedlog = prijedlozi.zakljucaj(proposalId);
		if (prijedlog.confirmedAt() != null) {
			throw new ChatProposalNotUsableException(Razlog.POTVRDJEN);
		}
		prijedlozi.odbij(proposalId, clock.instant());
	}

	private void provjeriDaVazi(Prijedlog prijedlog, Authentication authentication) {
		if (prijedlog.dismissedAt() != null) {
			throw new ChatProposalNotUsableException(Razlog.ODBIJEN);
		}
		if (!clock.instant().isBefore(prijedlog.expiresAt())) {
			throw new ChatProposalNotUsableException(Razlog.ISTEKAO);
		}
		if (prijedlog.resourceId() != null) {
			// Od prijedloga do klika admin je mogao ugasiti resurs ili mu staviti cijenu
			ResourceResponse resurs = resources.get(prijedlog.resourceId());
			if (!resourceAccess.canView(resurs.id(), authentication)) {
				throw new ChatProposalNotUsableException(Razlog.RESURS_NEDOSTUPAN);
			}
			if (resurs.priceCents() > 0) {
				throw new ChatProposalNotUsableException(Razlog.PLACA_SE);
			}
		}
	}

	// Ključ je nov za svaki prijedlog: dva prijedloga istog termina su dva zahtjeva, a ne ponovljen jedan
	private UUID upisi(UUID sesija, String akcija, UUID resurs, Instant pocetak, UUID rezervacija) {
		UUID id = UUID.randomUUID();
		Instant sada = clock.instant();
		prijedlozi.novi(id, sesija, akcija, resurs, pocetak, rezervacija, "chat-" + id, sada, sada.plus(trajanje));
		return id;
	}

}
