package ba.slotbook.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.auth.RandomToken;

@Service
public class CalendarFeedService {

	// Prošle rezervacije ostaju u kalendaru mjesec dana, da se vidi gdje je čovjek bio, a stare ne rastu bez kraja
	static final Duration PROSLOST = Duration.ofDays(30);

	private final CalendarFeedRepository feeds;
	private final BookingRepository bookings;
	private final Clock clock;

	public CalendarFeedService(CalendarFeedRepository feeds, BookingRepository bookings, Clock clock) {
		this.feeds = feeds;
		this.bookings = bookings;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public CalendarFeedResponse status(UUID userId) {
		return feeds.findById(userId)
				.map(feed -> new CalendarFeedResponse(true, feed.getCreatedAt(), null))
				.orElse(new CalendarFeedResponse(false, null, null));
	}

	// Token se vidi samo u ovom odgovoru. Baza čuva hash, pa izgubljen link se ne može ponovo
	// prikazati, nego se pravi novi, a stari time prestaje raditi.
	@Transactional
	public CalendarFeedResponse replace(UUID userId) {
		String token = RandomToken.generate();
		Instant sada = clock.instant();
		feeds.replace(userId, RandomToken.sha256(token), sada);
		return new CalendarFeedResponse(true, sada, token);
	}

	@Transactional
	public void delete(UUID userId) {
		feeds.deleteForUser(userId);
	}

	// Pravo daje samo token iz linka: kalendar na telefonu ne zna za prijavu ni JWT
	@Transactional(readOnly = true)
	public String ics(String token) {
		if (token == null || token.isBlank()) {
			throw new CalendarFeedNotFoundException();
		}
		CalendarFeed feed = feeds.findByTokenHash(RandomToken.sha256(token))
				.orElseThrow(CalendarFeedNotFoundException::new);
		Instant sada = clock.instant();
		return BookingCalendar.feed(bookings.findForCalendar(feed.getUserId(), BookingCalendar.U_KALENDARU,
				sada.minus(PROSLOST)), sada);
	}

}
