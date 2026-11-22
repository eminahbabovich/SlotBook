package ba.slotbook.booking;

import java.io.IOException;
import java.time.Duration;
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

// Otvoreni kalendari po resursu. Samo u memoriji ove instance: sa dvije instance iza load
// balancera promjena s jedne ne bi stigla do kalendara spojenog na drugu (docs/ODLUKE.md).
@Component
public class SlotStream {

	// Koliko traje access token. Poslije toga frontend otvori novu vezu s novim tokenom, pa član
	// kojeg su izbacili iz organizacije ne prima signale duže od toga.
	static final Duration TRAJE = Duration.ofMinutes(15);

	private final Map<UUID, Set<SseEmitter>> veze = new ConcurrentHashMap<>();

	SseEmitter open(UUID resourceId) {
		SseEmitter veza = new SseEmitter(TRAJE.toMillis());
		Set<SseEmitter> resursa = veze.computeIfAbsent(resourceId, id -> new CopyOnWriteArraySet<>());
		resursa.add(veza);
		Runnable ukloni = () -> resursa.remove(veza);
		veza.onCompletion(ukloni);
		veza.onTimeout(ukloni);
		veza.onError(greska -> ukloni.run());
		// Prvi red odmah: proxy i browser tek tada vide zaglavlja, a frontend zna da je veza živa
		posalji(veza, SseEmitter.event().comment("spojeno"));
		return veza;
	}

	// Tek poslije commita: rezervacija koja je pala na EXCLUDE ne smije javiti promjenu, a
	// kalendar koji odmah pita /slots mora vidjeti novo stanje
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void promijenjeno(SlotsChanged dogadjaj) {
		for (SseEmitter veza : veze.getOrDefault(dogadjaj.resourceId(), Set.of())) {
			posalji(veza, SseEmitter.event().name("promjena").data(dogadjaj.resourceId().toString()));
		}
	}

	// Proxy (Render, Vite) zatvori vezu koja dugo šuti. Komentar browser ne prikazuje kao događaj.
	@Scheduled(fixedDelay = 25_000)
	void otkucaj() {
		veze.values().forEach(resursa -> resursa.forEach(veza -> posalji(veza, SseEmitter.event().comment("ziv"))));
	}

	int brojVeza(UUID resourceId) {
		return veze.getOrDefault(resourceId, Set.of()).size();
	}

	// Zatvoren tab se vidi tek kad pisanje padne. Tada se veza zatvori, a onError je ukloni.
	private static void posalji(SseEmitter veza, SseEmitter.SseEventBuilder dogadjaj) {
		try {
			veza.send(dogadjaj);
		} catch (IOException | IllegalStateException e) {
			veza.completeWithError(e);
		}
	}

}
