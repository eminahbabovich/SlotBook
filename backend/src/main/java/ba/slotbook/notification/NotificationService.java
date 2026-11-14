package ba.slotbook.notification;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.user.UserRepository;

@Service
public class NotificationService {

	private final NotificationRepository notifications;
	private final UserRepository users;
	private final Clock clock;

	public NotificationService(NotificationRepository notifications, UserRepository users, Clock clock) {
		this.notifications = notifications;
		this.users = users;
		this.clock = clock;
	}

	// MANDATORY: bez transakcije pozivaoca Spring baci izuzetak. Obavijest upisana u svojoj
	// transakciji ostala bi i kad rezervacija padne, a outbox postoji baš da se to ne može desiti.
	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueueForBooking(UUID userId, NotificationType type, UUID bookingId) {
		notifications.save(new Notification(users.getReferenceById(userId), type, bookingId, clock.instant()));
	}

}
