package ba.slotbook.export;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.export.MyDataExport.Profile;

@Service
public class MyDataExportService {

	private final MyDataRepository data;
	private final Clock clock;

	public MyDataExportService(MyDataRepository data, Clock clock) {
		this.data = data;
		this.clock = clock;
	}

	// REPEATABLE_READ: svih jedanaest upita vidi isti snimak baze. Pod READ COMMITTED bi rezervacija
	// nastala između dva upita mogla biti u historiji, a ne u listi rezervacija.
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public MyDataExport export(UUID userId) {
		Profile korisnik = data.profile(userId);
		return new MyDataExport(clock.instant(), korisnik, data.memberships(userId), data.bookings(userId),
				data.bookingEvents(userId), data.managedResources(userId),
				data.notifications(userId, korisnik.email()), data.sessions(userId), data.emailLinks(userId),
				data.invites(korisnik.email()), data.loginFailures(korisnik.email()).orElse(null),
				data.auditEntries(userId));
	}

}
