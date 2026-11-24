package ba.slotbook.account;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// SQL preko JdbcClient, kao izvoz: brisanje dira više od deset tabela iz osam paketa
@Repository
class AccountDeletionRepository {

	record Membership(UUID organizationId, String role) {
	}

	private final JdbcClient jdbc;

	AccountDeletionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// PostgreSQL driver Instant ne zna poslati kao parametar ("Can't infer the SQL type"), a OffsetDateTime
	// zna. U UTC-u, da vrijednost ne zavisi od zone servera.
	private static OffsetDateTime utc(Instant trenutak) {
		return trenutak.atOffset(ZoneOffset.UTC);
	}

	// Dva admina iste organizacije koji brišu nalog u istom trenutku: drugi ovdje čeka dok prvi ne
	// završi, pa tek onda broji admine. NO KEY UPDATE, a ne UPDATE: nove rezervacije, resursi i članovi
	// (strani ključ na organizaciju) zbog ovoga ne čekaju.
	void lockAdminOrganizations(UUID userId) {
		jdbc.sql("""
				select o.id from organizations o
				join organization_members m on m.organization_id = o.id and m.user_id = ? and m.role = 'ADMIN'
				order by o.id for no key update of o""").param(userId).query().listOfRows();
	}

	List<String> organizationsWithOnlyThisAdmin(UUID userId) {
		return jdbc.sql("""
				select o.name from organizations o
				join organization_members m on m.organization_id = o.id and m.user_id = ? and m.role = 'ADMIN'
				where (select count(*) from organization_members a
				       where a.organization_id = o.id and a.role = 'ADMIN') = 1
				order by o.name""").param(userId).query(String.class).list();
	}

	// I neaktivan resurs: strani ključ resources_provider_clan ga veže za članstvo
	List<String> ledResources(UUID userId) {
		return jdbc.sql("select name from resources where provider_id = ? order by name").param(userId)
				.query(String.class).list();
	}

	List<Membership> memberships(UUID userId) {
		return jdbc.sql("select organization_id, role from organization_members where user_id = ?").param(userId)
				.query(Membership.class).list();
	}

	// Kao BookingJob: jedna naredba mijenja status i piše historiju. Prošle i one koje upravo traju ostaju.
	// RETURNING daje novi status, a glavni upit vidi bookings prije izmjene iz WITH, pa b.status je stari.
	// Vraća otkazane, da plaćanje vrati novac za plaćene.
	List<UUID> cancelFutureBookings(UUID userId, Instant sada) {
		return jdbc.sql("""
				with otkazane as (
					update bookings set status = 'CANCELLED', version = version + 1
					where client_id = :korisnik and status in ('PENDING', 'CONFIRMED') and start_time > :sada
					returning id)
				insert into booking_events (id, booking_id, from_status, to_status, actor_id, created_at)
				select gen_random_uuid(), b.id, b.status, 'CANCELLED', :korisnik, :sada
				from otkazane o join bookings b on b.id = o.id
				returning booking_id""")
				.param("korisnik", userId).param("sada", utc(sada)).query(UUID.class).list();
	}

	void revokeSessions(UUID userId, Instant sada) {
		jdbc.sql("update refresh_tokens set revoked_at = ? where user_id = ? and revoked_at is null")
				.param(utc(sada)).param(userId).update();
	}

	void deletePersonalRows(UUID userId, String email) {
		jdbc.sql("delete from user_tokens where user_id = ?").param(userId).update();
		jdbc.sql("delete from waitlist_entries where client_id = ?").param(userId).update();
		jdbc.sql("delete from calendar_feeds where user_id = ?").param(userId).update();
		jdbc.sql("delete from notifications where user_id = ? or email = ?").param(userId).param(email).update();
		jdbc.sql("delete from organization_invites where email = ?").param(email).update();
		jdbc.sql("delete from login_failures where email = ?").param(email).update();
		jdbc.sql("delete from organization_members where user_id = ?").param(userId).update();
	}

	void anonymize(UUID userId, Instant sada) {
		jdbc.sql("""
				update users set email = 'obrisan-' || id || '@slotbook.invalid', display_name = 'Obrisan nalog',
				       password_hash = '!', email_verified_at = null, deleted_at = ?
				where id = ?""").param(utc(sada)).param(userId).update();
	}

}
