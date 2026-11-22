package ba.slotbook.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.organization.Organization;
import ba.slotbook.organization.OrganizationAccess;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;
import jakarta.persistence.EntityManager;

@Import({ TestcontainersConfiguration.class, ResourceAccess.class, OrganizationAccess.class })
@DataJpaTest
class ResourceAccessTest {

	private static final Instant SADA = Instant.parse("2026-11-13T10:00:00Z");

	@Autowired
	ResourceAccess access;

	@Autowired
	ResourceRepository resources;

	@Autowired
	UserRepository users;

	@Autowired
	EntityManager em;

	@Autowired
	JdbcTemplate jdbc;

	private User emina;
	private User kenan;
	private User dzenan;
	private User tarik;
	private UUID kenanovTrening;
	private UUID eminaSauna;
	private UUID zatvorenaSala;

	// Teretana: Emina admin, Kenan trener, Dženan klijent. Tarik je admin čitaonice.
	@BeforeEach
	void podaci() {
		emina = korisnik("emina");
		kenan = korisnik("kenan");
		dzenan = korisnik("dzenan");
		tarik = korisnik("tarik");
		Organization teretana = organizacija("teretana-pristup");
		Organization citaonica = organizacija("citaonica-pristup");
		clan(teretana, emina, "ADMIN");
		clan(teretana, kenan, "PROVIDER");
		clan(teretana, dzenan, "CLIENT");
		clan(citaonica, tarik, "ADMIN");

		kenanovTrening = resources.save(new Resource(teretana, kenan, "Kružni trening", null, 60, true, 0, 1, SADA)).getId();
		eminaSauna = resources.save(new Resource(teretana, emina, "Sauna", null, 30, true, 0, 1, SADA)).getId();
		Resource sala = new Resource(teretana, emina, "Stara sala", null, 60, true, 0, 1, SADA);
		sala.deactivate();
		zatvorenaSala = resources.saveAndFlush(sala).getId();
	}

	private User korisnik(String ime) {
		return users.saveAndFlush(new User(ime + "@example.com", "hash", ime, SADA));
	}

	private Organization organizacija(String slug) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into organizations values (?, ?, ?, 'Europe/Sarajevo', now())", id, slug, slug);
		return em.getReference(Organization.class, id);
	}

	private void clan(Organization organizacija, User korisnik, String uloga) {
		jdbc.update("insert into organization_members values (?, ?, ?, now())", organizacija.getId(), korisnik.getId(),
				uloga);
	}

	private JwtAuthenticationToken prijavljen(User korisnik) {
		Jwt jwt = Jwt.withTokenValue("nebitan").header("alg", "HS256").subject(korisnik.getId().toString()).build();
		return new JwtAuthenticationToken(jwt);
	}

	@Test
	void adminUredjujeSvakiResursSvojeOrganizacije() {
		assertThat(access.canManage(kenanovTrening, prijavljen(emina))).isTrue();
		assertThat(access.canManage(eminaSauna, prijavljen(emina))).isTrue();
	}

	@Test
	void trenerUredjujeSamoResursKojiVodi() {
		assertThat(access.canManage(kenanovTrening, prijavljen(kenan))).isTrue();
		assertThat(access.canManage(eminaSauna, prijavljen(kenan))).isFalse();
	}

	// Provider koji je postao klijent više ne uređuje ni resurs koji je vodio
	@Test
	void klijentNeUredjujeNiResursKojiJeVodio() {
		jdbc.update("update organization_members set role = 'CLIENT' where user_id = ?", kenan.getId());

		assertThat(access.canManage(kenanovTrening, prijavljen(kenan))).isFalse();
		assertThat(access.canManage(kenanovTrening, prijavljen(dzenan))).isFalse();
	}

	@Test
	void klijentVidiSamoAktivanResursAOsobljeIDeaktiviran() {
		assertThat(access.canView(kenanovTrening, prijavljen(dzenan))).isTrue();
		assertThat(access.canView(zatvorenaSala, prijavljen(dzenan))).isFalse();
		assertThat(access.canView(zatvorenaSala, prijavljen(kenan))).isTrue();
	}

	@Test
	void adminDrugeOrganizacijeNiVidiNiUredjuje() {
		assertThat(access.canView(kenanovTrening, prijavljen(tarik))).isFalse();
		assertThat(access.canManage(kenanovTrening, prijavljen(tarik))).isFalse();
	}

	@Test
	void nepostojeciResursINeprijavljenKorisnikNemajuPristup() {
		var anonimni = new AnonymousAuthenticationToken("kljuc", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

		assertThat(access.canView(UUID.randomUUID(), prijavljen(emina))).isFalse();
		assertThat(access.canManage(kenanovTrening, anonimni)).isFalse();
		assertThat(access.canView(kenanovTrening, anonimni)).isFalse();
	}

}
