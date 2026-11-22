package ba.slotbook.export;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ba.slotbook.export.MyDataExport.AuditEntry;
import ba.slotbook.export.MyDataExport.Booking;
import ba.slotbook.export.MyDataExport.BookingEvent;
import ba.slotbook.export.MyDataExport.CalendarFeed;
import ba.slotbook.export.MyDataExport.EmailLink;
import ba.slotbook.export.MyDataExport.Invite;
import ba.slotbook.export.MyDataExport.LoginFailures;
import ba.slotbook.export.MyDataExport.ManagedResource;
import ba.slotbook.export.MyDataExport.Membership;
import ba.slotbook.export.MyDataExport.Notification;
import ba.slotbook.export.MyDataExport.Profile;
import ba.slotbook.export.MyDataExport.Session;
import ba.slotbook.export.MyDataExport.WaitlistEntry;

// SQL umjesto JPA: izvoz čita iz deset tabela iz osam paketa, a ovako svaki upit izričito nabraja
// kolone koje izlaze. Kolona se na polje recorda veže po imenu (snake_case -> camelCase).
@Repository
class MyDataRepository {

	private final JdbcClient jdbc;

	MyDataRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Profile profile(UUID userId) {
		return jdbc.sql("select id, email, display_name, created_at, email_verified_at from users where id = ?")
				.param(userId).query(Profile.class).single();
	}

	List<Membership> memberships(UUID userId) {
		return jdbc.sql("""
				select o.id as organization_id, o.name as organization_name, m.role, m.joined_at
				from organization_members m join organizations o on o.id = m.organization_id
				where m.user_id = ? order by m.joined_at""").param(userId).query(Membership.class).list();
	}

	List<Booking> bookings(UUID userId) {
		return jdbc.sql("""
				select b.id, o.name as organization_name, r.name as resource_name, b.start_time, b.end_time,
				       b.status, b.created_at
				from bookings b join resources r on r.id = b.resource_id join organizations o on o.id = r.organization_id
				where b.client_id = ? order by b.start_time""").param(userId).query(Booking.class).list();
	}

	// I završeni upisi (istekli, otkazani): i oni su podatak o korisniku dok postoje u bazi
	List<WaitlistEntry> waitlistEntries(UUID userId) {
		return jdbc.sql("""
				select o.name as organization_name, r.name as resource_name, w.start_time, w.end_time, w.status,
				       w.offered_until, w.created_at
				from waitlist_entries w join resources r on r.id = w.resource_id
				join organizations o on o.id = r.organization_id
				where w.client_id = ? order by w.start_time""").param(userId).query(WaitlistEntry.class).list();
	}

	Optional<CalendarFeed> calendarFeed(UUID userId) {
		return jdbc.sql("select created_at from calendar_feeds where user_id = ?").param(userId)
				.query(CalendarFeed.class).optional();
	}

	// Historija samo korisnikovih rezervacija, ne svega što je mijenjao kao provider
	List<BookingEvent> bookingEvents(UUID userId) {
		return jdbc.sql("""
				select e.booking_id, e.from_status, e.to_status, coalesce(e.actor_id = ?, false) as by_me, e.created_at
				from booking_events e join bookings b on b.id = e.booking_id
				where b.client_id = ? order by e.created_at""").param(userId).param(userId)
				.query(BookingEvent.class).list();
	}

	List<ManagedResource> managedResources(UUID userId) {
		return jdbc.sql("""
				select r.id, o.name as organization_name, r.name, r.active, r.created_at
				from resources r join organizations o on o.id = r.organization_id
				where r.provider_id = ? order by r.created_at""").param(userId).query(ManagedResource.class).list();
	}

	// Mail pozivnice ide na email, bez user_id, pa se traži i po emailu
	List<Notification> notifications(UUID userId, String email) {
		return jdbc.sql("""
				select type, status, subject, created_at, sent_at from notifications
				where user_id = ? or email = ? order by created_at""").param(userId).param(email)
				.query(Notification.class).list();
	}

	List<Session> sessions(UUID userId) {
		return jdbc.sql("""
				select created_at, expires_at, revoked_at from refresh_tokens
				where user_id = ? order by created_at""").param(userId).query(Session.class).list();
	}

	List<EmailLink> emailLinks(UUID userId) {
		return jdbc.sql("""
				select type, created_at, expires_at, used_at from user_tokens
				where user_id = ? order by created_at""").param(userId).query(EmailLink.class).list();
	}

	List<Invite> invites(String email) {
		return jdbc.sql("""
				select o.name as organization_name, i.role, i.created_at, i.expires_at, i.accepted_at
				from organization_invites i join organizations o on o.id = i.organization_id
				where i.email = ? order by i.created_at""").param(email).query(Invite.class).list();
	}

	Optional<LoginFailures> loginFailures(String email) {
		return jdbc.sql("select failures, window_start, locked_until from login_failures where email = ?")
				.param(email).query(LoginFailures.class).optional();
	}

	List<AuditEntry> auditEntries(UUID userId) {
		return jdbc.sql("""
				select o.name as organization_name, a.action, a.target_id, a.detail, a.created_at
				from audit_log a join organizations o on o.id = a.organization_id
				where a.actor_id = ? order by a.id""").param(userId).query(AuditEntry.class).list();
	}

}
