package ba.slotbook.chat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ba.slotbook.chat.ChatModel.Poruka;
import tools.jackson.databind.json.JsonMapper;

// SQL preko JdbcClient, kao brisanje naloga: poruka je jsonb u obliku koji diktira API, a brojač
// tokena je upsert. Entitet bi tu samo smetao.
@Repository
class ChatRepository {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final JdbcClient jdbc;

	ChatRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void novaSesija(UUID id, UUID organizacija, UUID korisnik, Instant sada) {
		jdbc.sql("""
				insert into chat_sessions (id, organization_id, user_id, created_at, last_message_at)
				values (?, ?, ?, ?, ?)""")
				.params(id, organizacija, korisnik, Timestamp.from(sada), Timestamp.from(sada))
				.update();
	}

	Optional<UUID> vlasnik(UUID sesija) {
		return jdbc.sql("select user_id from chat_sessions where id = ?").param(sesija).query(UUID.class).optional();
	}

	// Red sesije ostaje zaključan do kraja transakcije, pa druga poruka u istoj sesiji čeka i vidi novi broj
	int zakljucajIBroj(UUID sesija) {
		return jdbc.sql("select message_count from chat_sessions where id = ? for update").param(sesija)
				.query(Integer.class).single();
	}

	void dopisi(UUID sesija, int seq, Poruka poruka, Instant sada) {
		jdbc.sql("""
				insert into chat_messages (session_id, seq, role, content, created_at)
				values (?, ?, ?, cast(? as jsonb), ?)""")
				.params(sesija, seq, poruka.role(), JSON.writeValueAsString(poruka), Timestamp.from(sada))
				.update();
	}

	void zapisiBroj(UUID sesija, int broj, Instant sada) {
		jdbc.sql("update chat_sessions set message_count = ?, last_message_at = ? where id = ?")
				.params(broj, Timestamp.from(sada), sesija)
				.update();
	}

	List<SacuvanaPoruka> poruke(UUID sesija) {
		return jdbc.sql("select content::text as content, created_at from chat_messages where session_id = ? order by seq")
				.param(sesija)
				.query((red, i) -> new SacuvanaPoruka(JSON.readValue(red.getString("content"), Poruka.class),
						red.getTimestamp("created_at").toInstant()))
				.list();
	}

	void dodajTokene(UUID korisnik, LocalDate dan, long tokeni) {
		jdbc.sql("""
				insert into chat_usage (user_id, day, tokens) values (?, ?, ?)
				on conflict (user_id, day) do update set tokens = chat_usage.tokens + excluded.tokens""")
				.params(korisnik, dan, tokeni)
				.update();
	}

	long tokeniKorisnika(UUID korisnik, LocalDate dan) {
		return jdbc.sql("select coalesce(sum(tokens), 0) from chat_usage where user_id = ? and day = ?")
				.params(korisnik, dan).query(Long.class).single();
	}

	long tokeniSvih(LocalDate dan) {
		return jdbc.sql("select coalesce(sum(tokens), 0) from chat_usage where day = ?").param(dan)
				.query(Long.class).single();
	}

	record SacuvanaPoruka(Poruka poruka, Instant vrijeme) {
	}

}
