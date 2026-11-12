package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Import({ TestcontainersConfiguration.class, OrganizationAccess.class })
@DataJpaTest
class OrganizationAccessTest {

	private static final Instant SADA = Instant.parse("2026-11-12T10:00:00Z");

	@Autowired
	OrganizationAccess access;

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	OrganizationMemberRepository members;

	@Autowired
	UserRepository users;

	private Organization citaonica;
	private Organization teretana;
	private User lejla;

	@BeforeEach
	void lejlaJeAdminCitaoniceIKlijentTeretane() {
		lejla = users.saveAndFlush(new User("lejla@example.com", "hash", "Lejla", SADA));
		citaonica = organizations.saveAndFlush(new Organization("Čitaonica FIT", "citaonica-fit", "Europe/Sarajevo", SADA));
		teretana = organizations.saveAndFlush(new Organization("Teretana Grbavica", "teretana-grbavica", "Europe/Sarajevo", SADA));
		members.saveAndFlush(new OrganizationMember(citaonica, lejla, OrganizationRole.ADMIN, SADA));
		members.saveAndFlush(new OrganizationMember(teretana, lejla, OrganizationRole.CLIENT, SADA));
	}

	private JwtAuthenticationToken prijavljena(User korisnik) {
		Jwt jwt = Jwt.withTokenValue("nebitan").header("alg", "HS256").subject(korisnik.getId().toString()).build();
		return new JwtAuthenticationToken(jwt);
	}

	@Test
	void adminImaAdminPristupSvojojOrganizaciji() {
		assertThat(access.hasRole(citaonica.getId(), prijavljena(lejla), OrganizationRole.ADMIN)).isTrue();
	}

	@Test
	void ulogaIzJedneOrganizacijeNeVaziUDrugoj() {
		assertThat(access.hasRole(teretana.getId(), prijavljena(lejla), OrganizationRole.ADMIN)).isFalse();
		assertThat(access.hasRole(teretana.getId(), prijavljena(lejla), OrganizationRole.ADMIN, OrganizationRole.CLIENT))
				.isTrue();
	}

	@Test
	void korisnikKojiNijeClanNemaPristup() {
		User emir = users.saveAndFlush(new User("emir@example.com", "hash", "Emir", SADA));

		assertThat(access.hasRole(citaonica.getId(), prijavljena(emir), OrganizationRole.ADMIN,
				OrganizationRole.PROVIDER, OrganizationRole.CLIENT)).isFalse();
	}

	@Test
	void anonimanKorisnikNemaPristup() {
		var anonimni = new AnonymousAuthenticationToken("kljuc", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

		assertThat(access.hasRole(citaonica.getId(), anonimni, OrganizationRole.CLIENT)).isFalse();
	}

}
