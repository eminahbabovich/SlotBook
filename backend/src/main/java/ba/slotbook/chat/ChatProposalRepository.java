package ba.slotbook.chat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ChatProposalRepository {

	private final JdbcClient jdbc;

	ChatProposalRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void novi(UUID id, UUID sesija, String akcija, UUID resurs, Instant pocetak, UUID rezervacija, String kljuc,
			Instant sada, Instant istice) {
		jdbc.sql("""
				insert into chat_proposals (id, session_id, action, resource_id, start_time, booking_id,
				    idempotency_key, created_at, expires_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
				.params(id, sesija, akcija, resurs, pocetak == null ? null : Timestamp.from(pocetak), rezervacija, kljuc,
						Timestamp.from(sada), Timestamp.from(istice))
				.update();
	}

	// Vlasnik prijedloga je vlasnik sesije u kojoj je nastao
	Optional<UUID> vlasnik(UUID prijedlog) {
		return jdbc.sql("""
				select s.user_id from chat_proposals p join chat_sessions s on s.id = p.session_id
				where p.id = ?""").param(prijedlog).query(UUID.class).optional();
	}

	// Druga potvrda istog prijedloga čeka ovdje dok prva ne završi, pa vidi da je već potvrđen
	Prijedlog zakljucaj(UUID prijedlog) {
		return jdbc.sql("""
				select p.*, s.organization_id, s.user_id from chat_proposals p
				join chat_sessions s on s.id = p.session_id
				where p.id = ? for update of p""").param(prijedlog).query(ChatProposalRepository::prijedlog).single();
	}

	void potvrdi(UUID prijedlog, UUID rezervacija, Instant sada) {
		jdbc.sql("update chat_proposals set confirmed_at = coalesce(confirmed_at, ?), booking_id = ? where id = ?")
				.params(Timestamp.from(sada), rezervacija, prijedlog)
				.update();
	}

	void odbij(UUID prijedlog, Instant sada) {
		jdbc.sql("update chat_proposals set dismissed_at = ? where id = ? and dismissed_at is null")
				.params(Timestamp.from(sada), prijedlog)
				.update();
	}

	private static Prijedlog prijedlog(ResultSet red, int i) throws SQLException {
		return new Prijedlog(red.getObject("id", UUID.class), red.getObject("organization_id", UUID.class),
				red.getObject("user_id", UUID.class), red.getString("action"), red.getObject("resource_id", UUID.class),
				trenutak(red, "start_time"), red.getObject("booking_id", UUID.class), red.getString("idempotency_key"),
				trenutak(red, "expires_at"), trenutak(red, "confirmed_at"), trenutak(red, "dismissed_at"));
	}

	private static Instant trenutak(ResultSet red, String kolona) throws SQLException {
		Timestamp vrijednost = red.getTimestamp(kolona);
		return vrijednost == null ? null : vrijednost.toInstant();
	}

	record Prijedlog(UUID id, UUID organizationId, UUID userId, String akcija, UUID resourceId, Instant startTime,
			UUID bookingId, String kljuc, Instant expiresAt, Instant confirmedAt, Instant dismissedAt) {
	}

}
