package ba.slotbook.screen;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ba.slotbook.booking.SlotsChanged;

// Otvoreni ekrani, kao SlotStream za kalendare, samo što je ključ ekran, a ne resurs: jedan ekran
// cijele organizacije prati sve njene resurse. Samo u memoriji ove instance (docs/ODLUKE.md).
@Component
class ScreenStream {

	// Poslije ovoga ekran otvori novu vezu i pri tom ponovo pita /api/screens/{token}. Tako i ekran
	// cijele organizacije vidi resurs napravljen u međuvremenu.
	static final Duration TRAJE = Duration.ofMinutes(15);

	record Pretplata(UUID screenId, List<UUID> resourceIds) {
	}

	private record Veza(SseEmitter emitter, Set<UUID> resursi) {
	}

	private final Map<UUID, Set<Veza>> veze = new ConcurrentHashMap<>();

	SseEmitter open(Pretplata pretplata) {
		SseEmitter emitter = new SseEmitter(TRAJE.toMillis());
		Veza veza = new Veza(emitter, Set.copyOf(pretplata.resourceIds()));
		// Kao u SlotStream: compute je atoman po ključu, a ekran bez veza ne ostaje u mapi
		veze.compute(pretplata.screenId(), (id, ekrana) -> {
			Set<Veza> skup = ekrana != null ? ekrana : new CopyOnWriteArraySet<>();
			skup.add(veza);
			return skup;
		});
		Runnable ukloni = () -> veze.computeIfPresent(pretplata.screenId(), (id, ekrana) -> {
			ekrana.remove(veza);
			return ekrana.isEmpty() ? null : ekrana;
		});
		emitter.onCompletion(ukloni);
		emitter.onTimeout(ukloni);
		emitter.onError(greska -> ukloni.run());
		posalji(emitter, SseEmitter.event().comment("spojeno"));
		return emitter;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void promijenjeno(SlotsChanged dogadjaj) {
		veze.values().forEach(ekrana -> ekrana.stream()
				.filter(veza -> veza.resursi().contains(dogadjaj.resourceId()))
				.forEach(veza -> posalji(veza.emitter(), SseEmitter.event().name("promjena")
						.data(dogadjaj.resourceId().toString()))));
	}

	// Ugašen ekran odmah gubi vezu. Ponovno spajanje dobije 404, pa ekran kaže da je ugašen. Veza koja
	// se otvarala baš dok se ekran gasio (provjera prošla prije commita) može ostati do isteka, najviše
	// TRAJE. Nisam to zatvarao: veza nosi samo id resursa, a podatke ekran traži preko tokena, koji je tada 404.
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void ugasen(DoorScreenRevoked dogadjaj) {
		Set<Veza> ekrana = veze.remove(dogadjaj.screenId());
		if (ekrana != null) {
			ekrana.forEach(veza -> veza.emitter().complete());
		}
	}

	@Scheduled(fixedDelay = 25_000)
	void otkucaj() {
		veze.values().forEach(ekrana -> ekrana.forEach(veza -> posalji(veza.emitter(),
				SseEmitter.event().comment("ziv"))));
	}

	int brojVeza(UUID screenId) {
		return veze.getOrDefault(screenId, Set.of()).size();
	}

	private static void posalji(SseEmitter veza, SseEmitter.SseEventBuilder dogadjaj) {
		try {
			veza.send(dogadjaj);
		} catch (IOException | IllegalStateException e) {
			veza.completeWithError(e);
		}
	}

}
