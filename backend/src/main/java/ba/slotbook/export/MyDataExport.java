package ba.slotbook.export;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Sve što aplikacija zna o jednom korisniku. Svaki dio je svoj record s tačno onim kolonama koje
// izlaze, pa hash lozinke, hashevi tokena i tijela mailova ne mogu ući ni slučajno.
public record MyDataExport(Instant exportedAt, Profile user, List<Membership> memberships, List<Booking> bookings,
		List<BookingEvent> bookingEvents, List<ManagedResource> managedResources, List<Notification> notifications,
		List<Session> sessions, List<EmailLink> emailLinks, List<Invite> invites, LoginFailures loginFailures,
		List<AuditEntry> auditEntries, List<WaitlistEntry> waitlistEntries, CalendarFeed calendarFeed) {

	public record Profile(UUID id, String email, String displayName, Instant createdAt, Instant emailVerifiedAt) {
	}

	public record Membership(UUID organizationId, String organizationName, String role, Instant joinedAt) {
	}

	public record Booking(UUID id, String organizationName, String resourceName, Instant startTime, Instant endTime,
			String status, Instant createdAt) {
	}

	// byMe: promjenu je napravio korisnik sam. Ko je drugi, ne piše, jer su to podaci te osobe.
	public record BookingEvent(UUID bookingId, String fromStatus, String toStatus, boolean byMe, Instant createdAt) {
	}

	public record ManagedResource(UUID id, String organizationName, String name, boolean active, Instant createdAt) {
	}

	// Bez tijela: u mailu za potvrdu, reset i pozivnicu je link s pravim tokenom
	public record Notification(String type, String status, String subject, Instant createdAt, Instant sentAt) {
	}

	public record Session(Instant createdAt, Instant expiresAt, Instant revokedAt) {
	}

	public record EmailLink(String type, Instant createdAt, Instant expiresAt, Instant usedAt) {
	}

	public record Invite(String organizationName, String role, Instant createdAt, Instant expiresAt,
			Instant acceptedAt) {
	}

	public record LoginFailures(int failures, Instant windowStart, Instant lockedUntil) {
	}

	public record WaitlistEntry(String organizationName, String resourceName, Instant startTime, Instant endTime,
			String status, Instant offeredUntil, Instant createdAt) {
	}

	// Samo kad je link napravljen. Token se ne izvozi: baza ga nema, a i hash je tajna.
	public record CalendarFeed(Instant createdAt) {
	}

	public record AuditEntry(String organizationName, String action, UUID targetId, String detail,
			Instant createdAt) {
	}

}
