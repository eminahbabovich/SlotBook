package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

// Kroz pravu bazu, jer zauzet termin odbija EXCLUDE constraint, a ne Java
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingServiceTest {

	// Ponedjeljak, 19.11.2035. u 09:00 po sarajevskom vremenu (zimi +01:00)
	private static final Instant PONEDJELJAK_09 = Instant.parse("2035-11-19T08:00:00Z");

	@Autowired
	BookingService service;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ResourceService resourceService;

	@Autowired
	AvailabilityService availabilityService;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	BookingRepository bookings;

	@Autowired
	PlatformTransactionManager transakcije;

	private UUID profesorica;
	private UUID citaonica;
	private UUID studentica;

	@BeforeEach
	void citaonicaPonedjeljkomOdOsamDoDvanaest() {
		String oznaka = UUID.randomUUID().toString().substring(0, 8);
		profesorica = korisnik("amila." + oznaka + "@example.com", "Amila");
		studentica = korisnik("hana." + oznaka + "@example.com", "Hana");
		citaonica = organizationService.create(profesorica, "Čitaonica FIT", "citaonica-" + oznaka,
				"Europe/Sarajevo").id();
	}

	private UUID korisnik(String email, String ime) {
		return users.save(new User(email, "hash", ime, Instant.parse("2035-11-01T10:00:00Z"))).getId();
	}

	private UUID resurs(String naziv, int minuta, boolean autoConfirm) {
		UUID id = resourceService.create(citaonica, profesorica, naziv, null, minuta, autoConfirm, 0, 1, 0).id();
		availabilityService.replace(id,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
		return id;
	}

	// Grupna soba za učenje s tri mjesta: tri osobe dobiju mjesta 1, 2 i 3, a četvrta 409
	@Test
	void grupnaSobaPrimaTriOsobeNaRazlicitaMjestaIOdbijaCetvrtu() {
		UUID soba = resourceService.create(citaonica, profesorica, "Grupna soba 2", null, 60, true, 0, 3, 0).id();
		availabilityService.replace(soba,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

		for (String ime : List.of("Edina", "Kenan", "Jasmina")) {
			service.create(soba, korisnik(ime.toLowerCase() + "." + UUID.randomUUID() + "@example.com", ime),
					PONEDJELJAK_09, null);
		}

		assertThatThrownBy(() -> service.create(soba, studentica, PONEDJELJAK_09, null))
				.isInstanceOf(SlotTakenException.class);
		assertThat(jdbc.queryForList("select seat from bookings where resource_id = ? order by seat", Integer.class,
				soba)).containsExactly(1, 2, 3);
	}

	// Još ima mjesta, ali ista osoba ne može uzeti drugo
	@Test
	void istaOsobaNeMozeUzetiDrugoMjestoUIstomTerminu() {
		UUID soba = resourceService.create(citaonica, profesorica, "Grupna soba 3", null, 60, true, 0, 3, 0).id();
		availabilityService.replace(soba,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
		service.create(soba, studentica, PONEDJELJAK_09, null);

		assertThatThrownBy(() -> service.create(soba, studentica, PONEDJELJAK_09, null))
				.isInstanceOf(AlreadyInSlotException.class);
		assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id = ?", Long.class, soba))
				.isEqualTo(1);
	}

	@Test
	void naAutomatskojPotvrdiRezervacijaJePotvrdjenaAKrajIdeIzTrajanjaTermina() {
		UUID sto = resurs("Sto br. 3", 60, true);

		BookingResponse rezervacija = service.create(sto, studentica, PONEDJELJAK_09, null);

		assertThat(rezervacija.status()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(rezervacija.endTime()).isEqualTo(Instant.parse("2035-11-19T09:00:00Z"));
	}

	@Test
	void nastanakRezervacijeUpisujePrviDogadjajUHistoriji() {
		UUID konsultacije = resurs("Konsultacije, Baze podataka", 30, false);

		UUID id = service.create(konsultacije, studentica, PONEDJELJAK_09, null).id();

		assertThat(jdbc.queryForList("select from_status, to_status, actor_id from booking_events where booking_id = ?",
				id)).singleElement().satisfies(red -> {
					assertThat(red.get("from_status")).isNull();
					assertThat(red.get("to_status")).isEqualTo("PENDING");
					assertThat(red.get("actor_id")).isEqualTo(studentica);
				});
	}

	// Rok dolazi iz slotbook.booking.pending-hours (24) i upisuje se u bazu, ne računa se pri čitanju
	@Test
	void naRucnojPotvrdiRezervacijaCekaProvideraDanOdNastanka() {
		UUID konsultacije = resurs("Konsultacije, Baze podataka", 30, false);

		BookingResponse rezervacija = service.create(konsultacije, studentica, PONEDJELJAK_09, null);

		assertThat(rezervacija.status()).isEqualTo(BookingStatus.PENDING);
		assertThat(rezervacija.expiresAt()).isNotNull();
		assertThat(jdbc.queryForObject("select extract(epoch from expires_at - created_at) from bookings where id = ?",
				Double.class, rezervacija.id())).isEqualTo(86400.0);
	}

	// Sala u teretani za 15 KM: i s automatskom potvrdom čeka uplatu, a rok je 15 minuta
	// (slotbook.booking.payment-minutes), ne dan kao za potvrdu
	@Test
	void placenTerminCekaUplatuPetnaestMinutaIPamtiCijenuIzTrenutkaRezervacije() {
		UUID sala = resourceService.create(citaonica, profesorica, "Sala za grupni trening", null, 60, true, 0, 1, 1500)
				.id();
		availabilityService.replace(sala,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

		BookingResponse rezervacija = service.create(sala, studentica, PONEDJELJAK_09, null);

		assertThat(rezervacija.status()).isEqualTo(BookingStatus.PENDING);
		assertThat(jdbc.queryForObject("select extract(epoch from expires_at - created_at) from bookings where id = ?",
				Double.class, rezervacija.id())).isEqualTo(900.0);
		assertThat(jdbc.queryForObject("select price_cents from bookings where id = ?", Integer.class,
				rezervacija.id())).isEqualTo(1500);
	}

	// Provider tu nema šta potvrditi, pa mu ne ide mail "čeka potvrdu"
	@Test
	void zaRezervacijuKojaCekaUplatuProviderNeDobijaObavijest() {
		UUID sala = resourceService.create(citaonica, profesorica, "Sala za jogu", null, 60, false, 0, 1, 1000).id();
		availabilityService.replace(sala,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

		service.create(sala, studentica, PONEDJELJAK_09, null);

		assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Long.class,
				profesorica)).isZero();
	}

	@Test
	void naAutomatskojPotvrdiNemaRokaZaPotvrdu() {
		UUID sto = resurs("Sto br. 3", 60, true);

		assertThat(service.create(sto, studentica, PONEDJELJAK_09, null).expiresAt()).isNull();
	}

	// 09:30 je u radnom vremenu, ali satni termini počinju u 08, 09, 10 i 11
	@Test
	void pocetakKojiNijePoravnatNaTerminSeOdbija() {
		UUID sto = resurs("Sto br. 3", 60, true);

		assertThatThrownBy(() -> service.create(sto, studentica, Instant.parse("2035-11-19T08:30:00Z"), null))
				.isInstanceOf(InvalidFieldException.class)
				.hasFieldOrPropertyWithValue("polje", "startTime");
	}

	@Test
	void terminVanRadnogVremenaIUProslostiSeOdbijaju() {
		UUID sto = resurs("Sto br. 3", 60, true);

		// 12:00 je kraj radnog vremena, a utorak nema pravila
		assertThatThrownBy(() -> service.create(sto, studentica, Instant.parse("2035-11-19T11:00:00Z"), null))
				.isInstanceOf(InvalidFieldException.class);
		assertThatThrownBy(() -> service.create(sto, studentica, Instant.parse("2035-11-20T08:00:00Z"), null))
				.isInstanceOf(InvalidFieldException.class);
		// Ponedjeljak 16.11.2020. u 09:00, pravi termin, ali prošao
		assertThatThrownBy(() -> service.create(sto, studentica, Instant.parse("2020-11-16T08:00:00Z"), null))
				.isInstanceOf(InvalidFieldException.class);
	}

	@Test
	void zauzetTerminDajeSlotTakenIOstajeJednaRezervacija() {
		UUID sto = resurs("Sto br. 3", 60, true);
		UUID drugaStudentica = korisnik("lamija." + UUID.randomUUID() + "@example.com", "Lamija");
		service.create(sto, studentica, PONEDJELJAK_09, null);

		assertThatThrownBy(() -> service.create(sto, drugaStudentica, PONEDJELJAK_09, null))
				.isInstanceOf(SlotTakenException.class);
		assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id = ?", Long.class, sto))
				.isEqualTo(1);
	}

	// Dupli klik: drugi zahtjev ne pravi ništa novo, ni rezervaciju ni zapis u historiji
	@Test
	void istiKljucDvaputVracaIstuRezervaciju() {
		UUID sto = resurs("Sto br. 3", 60, true);

		BookingResponse prva = service.create(sto, studentica, PONEDJELJAK_09, "klik-1");
		BookingResponse druga = service.create(sto, studentica, PONEDJELJAK_09, "klik-1");

		assertThat(druga).isEqualTo(prva);
		assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id = ?", Long.class, sto))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from booking_events where booking_id = ?", Long.class,
				prva.id())).isEqualTo(1);
	}

	@Test
	void istiKljucZaDrugiTerminSeOdbija() {
		UUID sto = resurs("Sto br. 3", 60, true);
		service.create(sto, studentica, PONEDJELJAK_09, "klik-2");

		assertThatThrownBy(() -> service.create(sto, studentica, Instant.parse("2035-11-19T09:00:00Z"), "klik-2"))
				.isInstanceOf(IdempotencyKeyReusedException.class);
	}

	// Ključ je jedinstven po klijentu, a ne globalno: druga studentica ne dobija tuđu rezervaciju
	@Test
	void istiKljucDrugogKlijentaJeNovaRezervacija() {
		UUID sto = resurs("Sto br. 3", 60, true);
		UUID drugaStudentica = korisnik("lamija." + UUID.randomUUID() + "@example.com", "Lamija");
		BookingResponse hanina = service.create(sto, studentica, PONEDJELJAK_09, "klik-3");

		BookingResponse lamijina = service.create(sto, drugaStudentica, Instant.parse("2035-11-19T09:00:00Z"),
				"klik-3");

		assertThat(lamijina.id()).isNotEqualTo(hanina.id());
	}

	// Provider je otvorio rezervaciju (verzija 0), a studentica je u međuvremenu otkaže (verzija 1).
	// Providerova potvrda ne smije prepisati otkazivanje: UPDATE ... where version = 0 ne nađe red.
	@Test
	void zastarjelaRezervacijaSeNeMozeUpisatiPrekoNovije() {
		UUID konsultacije = resurs("Konsultacije, Baze podataka", 30, false);
		UUID id = service.create(konsultacije, studentica, PONEDJELJAK_09, null).id();
		TransactionTemplate providerova = new TransactionTemplate(transakcije);
		TransactionTemplate studenticina = new TransactionTemplate(transakcije);
		studenticina.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		assertThatThrownBy(() -> providerova.executeWithoutResult(s -> {
			Booking zastarjela = bookings.findById(id).orElseThrow();
			studenticina.executeWithoutResult(
					s2 -> service.changeStatus(id, studentica, BookingStatus.CANCELLED));
			zastarjela.prijedi(BookingStatus.CONFIRMED, Instant.parse("2035-11-18T10:00:00Z"));
			bookings.flush();
		})).isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, id))
				.isEqualTo("CANCELLED");
		assertThat(jdbc.queryForList("select to_status from booking_events where booking_id = ? order by created_at",
				String.class, id)).containsExactly("PENDING", "CANCELLED");
	}

	// Direktno u bazu, jer servis ne pravi rezervacije u prošlosti ni s rokom koji je već prošao
	private UUID upisi(UUID resurs, String status, Instant pocetak, Instant kraj, Instant rok) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at, "
				+ "expires_at) values (?, ?, ?, ?, ?, ?, now() - interval '3 days', ?)", id, resurs, studentica,
				utc(pocetak), utc(kraj), status, rok == null ? null : utc(rok));
		return id;
	}

	private static OffsetDateTime utc(Instant trenutak) {
		return trenutak.atOffset(ZoneOffset.UTC);
	}

	private String statusOd(UUID rezervacija) {
		return jdbc.queryForObject("select status from bookings where id = ?", String.class, rezervacija);
	}

	@Test
	void posaoPrebacujeUExpiredSamoOneKojimaJeRokProsao() {
		UUID konsultacije = resurs("Konsultacije, Baze podataka", 60, false);
		Instant sada = Instant.now();
		UUID istekla = upisi(konsultacije, "PENDING", PONEDJELJAK_09, PONEDJELJAK_09.plus(1, ChronoUnit.HOURS),
				sada.minus(1, ChronoUnit.MINUTES));
		UUID ceka = upisi(konsultacije, "PENDING", PONEDJELJAK_09.plus(1, ChronoUnit.HOURS),
				PONEDJELJAK_09.plus(2, ChronoUnit.HOURS), PONEDJELJAK_09);

		service.expireOverdue();

		assertThat(statusOd(istekla)).isEqualTo("EXPIRED");
		assertThat(statusOd(ceka)).isEqualTo("PENDING");
		assertThat(jdbc.queryForList("select from_status, to_status, actor_id from booking_events where booking_id = ?",
				istekla)).singleElement().satisfies(red -> {
					assertThat(red.get("from_status")).isEqualTo("PENDING");
					assertThat(red.get("to_status")).isEqualTo("EXPIRED");
					assertThat(red.get("actor_id")).isNull();
				});
	}

	// Posao još nije stigao (servis je spavao), a druga studentica želi isti termin
	@Test
	void isteklaRezervacijaNeSmetaNovojIPrijePosla() {
		UUID sto = resurs("Sto br. 3", 60, true);
		UUID drugaStudentica = korisnik("lamija." + UUID.randomUUID() + "@example.com", "Lamija");
		UUID istekla = upisi(sto, "PENDING", PONEDJELJAK_09, PONEDJELJAK_09.plus(1, ChronoUnit.HOURS),
				Instant.now().minus(1, ChronoUnit.MINUTES));

		BookingResponse nova = service.create(sto, drugaStudentica, PONEDJELJAK_09, null);

		assertThat(nova.status()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(statusOd(istekla)).isEqualTo("EXPIRED");
	}

	// Kraj prije 26 sati se zatvara, kraj prije 23 sata još čeka, jer provider ima dan za nedolazak
	@Test
	void potvrdjenaSeZatvaraTekDanPoslijeKraja() {
		UUID sto = resurs("Sto br. 3", 60, true);
		Instant sada = Instant.now();
		UUID davna = upisi(sto, "CONFIRMED", sada.minus(27, ChronoUnit.HOURS), sada.minus(26, ChronoUnit.HOURS), null);
		UUID jucerasnja = upisi(sto, "CONFIRMED", sada.minus(24, ChronoUnit.HOURS), sada.minus(23, ChronoUnit.HOURS),
				null);

		service.completeEnded();

		assertThat(statusOd(davna)).isEqualTo("COMPLETED");
		assertThat(statusOd(jucerasnja)).isEqualTo("CONFIRMED");
		assertThat(jdbc.queryForObject("select actor_id from booking_events where booking_id = ?", UUID.class, davna))
				.isNull();
	}

	// Provider je otvorio rezervaciju (verzija 0), a posao je u međuvremenu istekne. Kasna potvrda ne
	// smije oživjeti isteklu rezervaciju, jer bi termin možda već imao novu.
	@Test
	void kasnaPotvrdaNePrepisujeIstek() {
		UUID konsultacije = resurs("Konsultacije, Baze podataka", 60, false);
		UUID id = upisi(konsultacije, "PENDING", PONEDJELJAK_09, PONEDJELJAK_09.plus(1, ChronoUnit.HOURS),
				Instant.now().minus(1, ChronoUnit.MINUTES));
		TransactionTemplate providerova = new TransactionTemplate(transakcije);
		TransactionTemplate posao = new TransactionTemplate(transakcije);
		posao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		assertThatThrownBy(() -> providerova.executeWithoutResult(s -> {
			Booking zastarjela = bookings.findById(id).orElseThrow();
			posao.executeWithoutResult(s2 -> service.expireOverdue());
			zastarjela.prijedi(BookingStatus.CONFIRMED, Instant.parse("2035-11-18T10:00:00Z"));
			bookings.flush();
		})).isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(statusOd(id)).isEqualTo("EXPIRED");
	}

	// "TIP primalac" za svaku obavijest o rezervaciji, redom kako su nastale
	private List<String> obavijesti(UUID rezervacija) {
		return jdbc.queryForList("select type || ' ' || user_id from notifications where booking_id = ? "
				+ "order by created_at, type", String.class, rezervacija);
	}

	@Test
	void naRucnojPotvrdiObavijestIdeProfesoriciANaAutomatskojStudentici() {
		UUID konsultacije = resurs("Konsultacije, Baze podataka", 30, false);
		UUID sto = resurs("Sto br. 3", 60, true);

		UUID ceka = service.create(konsultacije, studentica, PONEDJELJAK_09, null).id();
		UUID potvrdjena = service.create(sto, studentica, PONEDJELJAK_09, null).id();

		assertThat(obavijesti(ceka)).containsExactly("BOOKING_AWAITS_CONFIRMATION " + profesorica);
		assertThat(obavijesti(potvrdjena)).containsExactly("BOOKING_CONFIRMED " + studentica);
	}

	@Test
	void potvrduIOdbijanjeSaznajeStudentica() {
		UUID konsultacije = resurs("Konsultacije, Baze podataka", 30, false);
		UUID prva = service.create(konsultacije, studentica, PONEDJELJAK_09, null).id();
		UUID druga = service.create(konsultacije, studentica, Instant.parse("2035-11-19T08:30:00Z"), null).id();

		service.changeStatus(prva, profesorica, BookingStatus.CONFIRMED);
		service.changeStatus(druga, profesorica, BookingStatus.REJECTED);

		assertThat(obavijesti(prva)).endsWith("BOOKING_CONFIRMED " + studentica);
		assertThat(obavijesti(druga)).endsWith("BOOKING_REJECTED " + studentica);
	}

	// Ko otkaže, taj zna. Obavijest ide drugoj strani.
	@Test
	void otkazivanjeSaznajeDrugaStrana() {
		UUID sto = resurs("Sto br. 3", 60, true);
		UUID otkazalaStudentica = service.create(sto, studentica, PONEDJELJAK_09, null).id();
		UUID otkazalaProfesorica = service.create(sto, studentica, Instant.parse("2035-11-19T09:00:00Z"), null).id();

		service.cancel(otkazalaStudentica, studentica, false);
		service.cancel(otkazalaProfesorica, profesorica, true);

		assertThat(obavijesti(otkazalaStudentica)).endsWith("BOOKING_CANCELLED " + profesorica);
		assertThat(obavijesti(otkazalaProfesorica)).endsWith("BOOKING_CANCELLED " + studentica);
	}

	// Za 23 sata dobija, za 25 još ne, a nepotvrđena ne dobija
	@Test
	void podsjetnikIdeStudenticiDanPrijePotvrdjenogTermina() {
		UUID sto = resurs("Sto br. 3", 60, true);
		Instant sada = Instant.now();
		UUID sutra = upisi(sto, "CONFIRMED", sada.plus(23, ChronoUnit.HOURS), sada.plus(24, ChronoUnit.HOURS), null);
		UUID prekosutra = upisi(sto, "CONFIRMED", sada.plus(25, ChronoUnit.HOURS), sada.plus(26, ChronoUnit.HOURS),
				null);
		UUID nepotvrdjena = upisi(sto, "PENDING", sada.plus(22, ChronoUnit.HOURS), sada.plus(23, ChronoUnit.HOURS),
				sada.plus(21, ChronoUnit.HOURS));

		service.sendReminders();

		assertThat(obavijesti(sutra)).containsExactly("BOOKING_REMINDER " + studentica);
		assertThat(obavijesti(prekosutra)).isEmpty();
		assertThat(obavijesti(nepotvrdjena)).isEmpty();
	}

	@Test
	void podsjetnikSeSaljeSamoJednomIBazaNePustaDrugi() {
		UUID sto = resurs("Sto br. 3", 60, true);
		Instant sada = Instant.now();
		UUID sutra = upisi(sto, "CONFIRMED", sada.plus(5, ChronoUnit.HOURS), sada.plus(6, ChronoUnit.HOURS), null);

		service.sendReminders();
		service.sendReminders();

		assertThat(obavijesti(sutra)).containsExactly("BOOKING_REMINDER " + studentica);
		assertThatThrownBy(() -> jdbc.update("insert into notifications (id, user_id, type, booking_id, status, "
				+ "next_attempt_at, created_at, subject, body) values (gen_random_uuid(), ?, 'BOOKING_REMINDER', ?, "
				+ "'PENDING', now(), now(), 'Podsjetnik', 'Termin')", studentica, sutra))
				.hasMessageContaining("notifications_jedan_podsjetnik");
	}

	// Rezervisano prije sat vremena za termin za 20 sati: potvrda je stigla maloprije
	@Test
	void rezervacijaZaManjeOdDanUnaprijedNeDobijaPodsjetnik() {
		UUID sto = resurs("Sto br. 3", 60, true);
		Instant sada = Instant.now();
		UUID skora = upisi(sto, "CONFIRMED", sada.plus(20, ChronoUnit.HOURS), sada.plus(21, ChronoUnit.HOURS), null);
		jdbc.update("update bookings set created_at = now() - interval '1 hour' where id = ?", skora);

		service.sendReminders();

		assertThat(obavijesti(skora)).isEmpty();
	}

	@Test
	void istekSaznajeStudentica() {
		UUID konsultacije = resurs("Konsultacije, Baze podataka", 60, false);
		UUID istekla = upisi(konsultacije, "PENDING", PONEDJELJAK_09, PONEDJELJAK_09.plus(1, ChronoUnit.HOURS),
				Instant.now().minus(1, ChronoUnit.MINUTES));

		service.expireOverdue();

		assertThat(obavijesti(istekla)).containsExactly("BOOKING_EXPIRED " + studentica);
	}

	// Outbox: rezervacija koja se poništi ne ostavlja obavijest o sebi
	@Test
	void ponistenaRezervacijaNeOstavljaObavijest() {
		UUID konsultacije = resurs("Konsultacije, Baze podataka", 30, false);

		new TransactionTemplate(transakcije).executeWithoutResult(s -> {
			service.create(konsultacije, studentica, PONEDJELJAK_09, null);
			s.setRollbackOnly();
		});

		assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Long.class,
				profesorica)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id = ?", Long.class,
				konsultacije)).isZero();
	}
}
