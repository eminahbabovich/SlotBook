package ba.slotbook.demo;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ba.slotbook.auth.LinkToken;

// Podaci za javni demo: čitaonica i teretana, tri računa s javnom lozinkom i nekoliko sedmica
// rezervacija. Postoji samo u profilu demo, jer prije punjenja obriše SVE iz baze.
// Upisuje se SQL-om, a ne kroz servise, jer servisi ne daju napraviti rezervaciju u prošlosti,
// a statistika bez prošlosti je prazna. Constrainti u bazi i dalje važe za svaki red.
@Component
@Profile("demo")
public class DemoPodaci {

	public static final String LOZINKA = "sebilj-na-bascarsiji";

	public static final List<DemoRacun> RACUNI = List.of(
			new DemoRacun("klijent@slotbook.test", "Hana Šehić", "rezerviše sto, konsultacije i trening"),
			new DemoRacun("provider@slotbook.test", "Mirza Begić", "vodi termine i potvrđuje konsultacije"),
			new DemoRacun("admin@slotbook.test", "Amila Kovačević", "uređuje resurse i članove, vidi statistiku"));

	// Ostali klijenti pune kalendar. Lozinku im ne zna niko, pa se ne može ući u njihov nalog.
	private static final List<String> OSTALI = List.of("Amar Delić", "Lejla Hadžić", "Tarik Čolić", "Ena Đozo",
			"Kenan Žujo", "Selma Mujić");

	private static final ZoneId ZONA = ZoneId.of("Europe/Sarajevo");
	private static final Set<DayOfWeek> RADNI_DANI = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
			DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
	private static final Duration TRAJANJE_DEMA = Duration.ofHours(24);
	private static final int POKUSAJA = 3;

	private static final Logger log = LoggerFactory.getLogger(DemoPodaci.class);

	private final JdbcTemplate jdbc;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final TransactionTemplate transakcija;
	private final LinkToken linkovi;

	public DemoPodaci(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, Clock clock,
			PlatformTransactionManager transactionManager, LinkToken linkovi) {
		this.jdbc = jdbc;
		this.linkovi = linkovi;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.transakcija = new TransactionTemplate(transactionManager);
	}

	// Demo se vrati na početak kad je stariji od 24 sata, ili odmah kad nekog demo računa nema
	// (posjetilac ga je obrisao, a obrisan nalog dobije drugi email). Vraća true ako je punio.
	// TRUNCATE zaključava tabele jednu po jednu, a pozadinski poslovi (obavijesti, istek rezervacija) ih
	// za to vrijeme čitaju svojim redom. Kad se zaključavanja ukrste, Postgres prekine jednu transakciju
	// (deadlock), i to je često baš ova. Prekinuta transakcija se ne može nastaviti, pa se cijela
	// ponavlja ispočetka. Zato petlja ide oko TransactionTemplate, a ne @Transactional na metodi.
	public boolean osvjeziAkoTreba() {
		for (int pokusaj = 1;; pokusaj++) {
			try {
				return Boolean.TRUE.equals(transakcija.execute(status -> osvjeziUTransakciji()));
			} catch (PessimisticLockingFailureException e) {
				if (pokusaj == POKUSAJA) {
					throw e;
				}
				log.warn("Vraćanje dema je izgubilo u deadlocku s drugom transakcijom, pokušaj {} od {}", pokusaj,
						POKUSAJA);
			}
		}
	}

	private boolean osvjeziUTransakciji() {
		Integer ispravnih = jdbc.queryForObject("""
				select count(*) from users
				where email in (?, ?, ?) and created_at > ?
				""", Integer.class, RACUNI.get(0).email(), RACUNI.get(1).email(), RACUNI.get(2).email(),
				utc(clock.instant().minus(TRAJANJE_DEMA)));
		if (ispravnih == RACUNI.size()) {
			return false;
		}
		obrisiSve();
		napuni();
		return true;
	}

	// Imena tabela dolaze iz kataloga baze i prolaze kroz quote_ident, pa nema unosa izvana.
	// Abecedni red je stalan: pg_tables bez njega vraća tabele redom koji se mijenja poslije svakog brisanja.
	// Flyway historija ostaje, inače bi Flyway pri sljedećem startu htio ponovo praviti šemu.
	private void obrisiSve() {
		List<String> tabele = jdbc.queryForList("""
				select quote_ident(tablename) from pg_tables
				where schemaname = current_schema() and tablename <> 'flyway_schema_history'
				order by tablename
				""", String.class);
		jdbc.execute("truncate table " + String.join(", ", tabele) + " cascade");
	}

	private void napuni() {
		Instant sada = clock.instant();
		LocalDate danas = LocalDate.ofInstant(sada, ZONA);

		UUID hana = korisnik(RACUNI.get(0), LOZINKA, sada);
		UUID mirza = korisnik(RACUNI.get(1), LOZINKA, sada);
		UUID amila = korisnik(RACUNI.get(2), LOZINKA, sada);
		List<UUID> ostali = OSTALI.stream()
				.map(ime -> korisnik(new DemoRacun(email(ime), ime, ""), UUID.randomUUID().toString(), sada))
				.toList();

		UUID citaonica = organizacija("Čitaonica PMF", "citaonica-pmf", sada);
		UUID teretana = organizacija("Teretana Ilidža", "teretana-ilidza", sada);
		for (UUID org : List.of(citaonica, teretana)) {
			clan(org, amila, "ADMIN", sada);
			clan(org, mirza, "PROVIDER", sada);
			clan(org, hana, "CLIENT", sada);
			ostali.forEach(klijent -> clan(org, klijent, "CLIENT", sada));
		}

		Resurs sto = resurs(citaonica, mirza, "Sto 4, tihi dio", "Četvrti sprat, bez razgovora.", 60, 1, 0, true,
				0, sada);
		radnoVrijeme(sto, RADNI_DANI, "08:00", "20:00");
		radnoVrijeme(sto, Set.of(DayOfWeek.SATURDAY), "09:00", "14:00");

		Resurs soba = resurs(citaonica, mirza, "Grupna soba 2",
				"Za učenje u grupi do četiri osobe. Tabla i flomasteri su u sobi.", 60, 4, 0, true, 0, sada);
		radnoVrijeme(soba, RADNI_DANI, "10:00", "18:00");

		Resurs konsultacije = resurs(citaonica, mirza, "Termin konsultacija",
				"Kabinet 112. Termin potvrđuje profesor, a otkazati se može do dva sata prije.", 30, 1, 0, false, 2,
				sada);
		radnoVrijeme(konsultacije, Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), "10:00", "12:00");

		Resurs sala = resurs(teretana, mirza, "Sala 3", "Sala s ogledalima, za samostalni trening.", 60, 1, 1500, true,
				0, sada);
		radnoVrijeme(sala, EnumSet.complementOf(EnumSet.of(DayOfWeek.SUNDAY)), "07:00", "21:00");

		Resurs trening = resurs(teretana, mirza, "Grupni trening", "Kružni trening, do osam osoba. Dođi pet minuta ranije.",
				60, 8, 0, true, 0, sada);
		radnoVrijeme(trening, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), "18:00", "20:00");

		prosleSedmice(danas, sada, mirza, ostali, sto, soba, konsultacije);
		narednaSedmica(danas, sada, hana, ostali, sto, soba, konsultacije, trening);
		hanineProsle(danas, sada, hana, sto);
		pozivi(citaonica, amila, sada);
		ekran(citaonica, amila, sada);
		pravila(citaonica, sto, konsultacije);
	}

	// Pravila koja se vide u demu: sto se drži najviše dvaput unaprijed i dvije sedmice naprijed, a
	// konsultacije se zakazuju najkasnije dva sata ranije. Tri nedolaska u mjesec dana blokiraju sedmicu.
	// Hana nema nedolazaka, pa nije blokirana.
	private void pravila(UUID citaonica, Resurs sto, Resurs konsultacije) {
		jdbc.update("update resources set max_active_per_member = 2, max_days_ahead = 14 where id = ?", sto.id());
		jdbc.update("update resources set min_notice_minutes = 120 where id = ?", konsultacije.id());
		jdbc.update("update organizations set no_show_limit = 3, no_show_window_days = 30, no_show_block_days = 7 "
				+ "where id = ?", citaonica);
	}

	// Admin odmah vidi pozivni link s plakatom (7 od 40 mjesta iskorišteno) i jednu pozivnicu koja čeka.
	// Token linka se računa iz ID-ja, isto kao u InviteLinkService, pa QR na plakatu stvarno radi.
	private void pozivi(UUID citaonica, UUID admin, Instant sada) {
		UUID link = UUID.randomUUID();
		jdbc.update("""
				insert into invite_links (id, organization_id, token_hash, max_uses, uses, expires_at, created_by, created_at)
				values (?, ?, ?, 40, 7, ?, ?, ?)""", link, citaonica,
				LinkToken.hash(linkovi.za(LinkToken.Svrha.POZIVNI_LINK, link)), utc(sada.plus(Duration.ofDays(30))), admin,
				utc(sada.minus(Duration.ofDays(3))));
		jdbc.update("""
				insert into organization_invites (id, organization_id, email, role, token_hash, created_at, expires_at)
				values (?, ?, 'asistentica.pmf@slotbook.test', 'PROVIDER', ?, ?, ?)""", UUID.randomUUID(), citaonica,
				LinkToken.hash(UUID.randomUUID().toString()), utc(sada.minus(Duration.ofDays(1))),
				utc(sada.plus(Duration.ofDays(6))));
	}

	// Ekran na vratima čitaonice, da admin u postavkama ima šta otvoriti i ugasiti
	private void ekran(UUID citaonica, UUID admin, Instant sada) {
		UUID ekran = UUID.randomUUID();
		jdbc.update("insert into door_screens (id, organization_id, token_hash, created_by, created_at) values (?, ?, ?, ?, ?)",
				ekran, citaonica, LinkToken.hash(linkovi.za(LinkToken.Svrha.EKRAN, ekran)), admin,
				utc(sada.minus(Duration.ofDays(2))));
	}

	// Četiri sedmice unazad, da statistika ima šta pokazati. Raspored je izmišljen, ali stalan:
	// isti dan daje iste termine. Sat 15 ostaje slobodan za Hanine rezervacije, a jučerašnje su još
	// CONFIRMED (posao ih zatvori dan poslije kraja), pa se ne smiju preklopiti.
	private void prosleSedmice(LocalDate danas, Instant sada, UUID provider, List<UUID> ostali, Resurs sto,
			Resurs soba, Resurs konsultacije) {
		int redni = 0;
		for (LocalDate dan = danas.minusDays(28); dan.isBefore(danas); dan = dan.plusDays(1)) {
			int d = dan.getDayOfYear();
			if (RADNI_DANI.contains(dan.getDayOfWeek())) {
				for (int sat = 8; sat < 20; sat++) {
					if (sat != 15 && (d + sat) % 3 == 0) {
						prosla(sto, ostali.get(redni % ostali.size()), u(dan, sat, 0), 1, redni++, provider, sada);
					}
				}
				int grupa = d % 4 + 1;
				for (int mjesto = 1; mjesto <= grupa; mjesto++) {
					prosla(soba, ostali.get((d + mjesto) % ostali.size()), u(dan, 12, 0), mjesto, redni++, provider,
							sada);
				}
			}
			if (dan.getDayOfWeek() == DayOfWeek.TUESDAY || dan.getDayOfWeek() == DayOfWeek.THURSDAY) {
				for (int i = 0; i < 4; i++) {
					if ((d + i) % 2 == 0) {
						prosla(konsultacije, ostali.get((d + i) % ostali.size()), u(dan, 10, 30 * i), 1, redni++,
								provider, sada);
					}
				}
			}
		}
	}

	private void narednaSedmica(LocalDate danas, Instant sada, UUID hana, List<UUID> ostali, Resurs sto, Resurs soba,
			Resurs konsultacije, Resurs trening) {
		Instant nastala = sada.minus(Duration.ofHours(2));
		int redni = 0;
		for (LocalDate dan = danas.plusDays(1); !dan.isAfter(danas.plusDays(6)); dan = dan.plusDays(1)) {
			int[] sati = RADNI_DANI.contains(dan.getDayOfWeek()) ? new int[] { 9, 12, 13, 17 }
					: dan.getDayOfWeek() == DayOfWeek.SATURDAY ? new int[] { 10, 11 } : new int[0];
			for (int sat : sati) {
				nova(sto, ostali.get(redni++ % ostali.size()), u(dan, sat, 0), 1, "CONFIRMED", nastala);
			}
		}

		LocalDate prviRadni = sljedeci(danas, RADNI_DANI);
		nova(sto, hana, u(prviRadni, 10, 0), 1, "CONFIRMED", nastala);
		// Tri od četiri mjesta zauzeta, pa kalendar kaže "ostalo jedno mjesto"
		for (int mjesto = 1; mjesto <= 3; mjesto++) {
			nova(soba, ostali.get(mjesto - 1), u(prviRadni, 14, 0), mjesto, "CONFIRMED", nastala);
		}
		LocalDate drugiRadni = sljedeci(prviRadni, RADNI_DANI);
		for (int mjesto = 1; mjesto <= 4; mjesto++) {
			nova(soba, ostali.get(mjesto + 1), u(drugiRadni, 11, 0), mjesto, "CONFIRMED", nastala);
		}

		// Hanina i Lejlina čekaju potvrdu, pa provider u rasporedu ima šta potvrditi
		LocalDate konsultacijeDan = sljedeci(danas, Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY));
		nova(konsultacije, hana, u(konsultacijeDan, 10, 0), 1, "PENDING", nastala);
		nova(konsultacije, ostali.get(0), u(konsultacijeDan, 10, 30), 1, "CONFIRMED", nastala);
		nova(konsultacije, ostali.get(1), u(konsultacijeDan, 11, 0), 1, "PENDING", nastala);

		LocalDate treningDan = sljedeci(danas, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
		for (int mjesto = 1; mjesto <= 5; mjesto++) {
			nova(trening, ostali.get(mjesto - 1), u(treningDan, 18, 0), mjesto, "CONFIRMED", nastala);
		}
		nova(trening, hana, u(treningDan, 18, 0), 6, "CONFIRMED", nastala);
		for (int mjesto = 1; mjesto <= 6; mjesto++) {
			nova(trening, ostali.get(mjesto - 1), u(treningDan, 19, 0), mjesto, "CONFIRMED", nastala);
		}
	}

	// Jedna završena i jedna otkazana, da Moje rezervacije pokažu i karte iz prošlosti
	private void hanineProsle(LocalDate danas, Instant sada, UUID hana, Resurs sto) {
		LocalDate dan = danas.minusDays(2);
		while (!RADNI_DANI.contains(dan.getDayOfWeek())) {
			dan = dan.minusDays(1);
		}
		prosla(sto, hana, u(dan, 15, 0), 1, 0, null, sada);
		LocalDate ranije = dan.minusDays(1);
		while (!RADNI_DANI.contains(ranije.getDayOfWeek())) {
			ranije = ranije.minusDays(1);
		}
		prosla(sto, hana, u(ranije, 15, 0), 1, 7, null, sada);
	}

	private static LocalDate sljedeci(LocalDate poslije, Set<DayOfWeek> dani) {
		LocalDate dan = poslije.plusDays(1);
		while (!dani.contains(dan.getDayOfWeek())) {
			dan = dan.plusDays(1);
		}
		return dan;
	}

	// Lokalno vrijeme u zoni organizacije, pa u UTC tek za konkretan dan: 10:00 ostaje 10:00 i
	// preko prelaska na ljetno vrijeme
	private static Instant u(LocalDate dan, int sat, int minuta) {
		return dan.atTime(LocalTime.of(sat, 0).plusMinutes(minuta)).atZone(ZONA).toInstant();
	}

	// Svaka deseta prošla je otkazana, a svaka deseta "nije došao". Ostale su završene, osim onih
	// kojima je kraj bio prije manje od dana: njih tek zatvara posao, kao i prave.
	private void prosla(Resurs resurs, UUID klijent, Instant pocetak, int mjesto, int redni, UUID provider,
			Instant sada) {
		Instant kraj = pocetak.plus(Duration.ofMinutes(resurs.minuta()));
		Instant nastala = pocetak.minus(Duration.ofDays(3));
		String status;
		Instant promjena;
		UUID akter;
		if (redni % 10 == 7) {
			status = "CANCELLED";
			promjena = pocetak.minus(Duration.ofDays(1));
			akter = klijent;
		} else if (redni % 10 == 4 && provider != null && kraj.plus(Duration.ofHours(1)).isBefore(sada)) {
			status = "NO_SHOW";
			promjena = kraj.plus(Duration.ofHours(1));
			akter = provider;
		} else if (kraj.plus(Duration.ofDays(1)).isBefore(sada)) {
			status = "COMPLETED";
			promjena = kraj.plus(Duration.ofDays(1));
			akter = null;
		} else {
			nova(resurs, klijent, pocetak, mjesto, "CONFIRMED", nastala);
			return;
		}
		UUID id = rezervacija(resurs, klijent, pocetak, kraj, mjesto, status, nastala, null);
		dogadjaj(id, null, "CONFIRMED", klijent, nastala);
		dogadjaj(id, "CONFIRMED", status, akter, promjena);
	}

	private void nova(Resurs resurs, UUID klijent, Instant pocetak, int mjesto, String status, Instant nastala) {
		Instant kraj = pocetak.plus(Duration.ofMinutes(resurs.minuta()));
		// Isti rok kao u pravoj rezervaciji: dan za potvrdu, a najkasnije do početka termina
		Instant rok = status.equals("PENDING") ? najranije(nastala.plus(Duration.ofHours(24)), pocetak) : null;
		UUID id = rezervacija(resurs, klijent, pocetak, kraj, mjesto, status, nastala, rok);
		dogadjaj(id, null, status, klijent, nastala);
	}

	private static Instant najranije(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	private UUID rezervacija(Resurs resurs, UUID klijent, Instant pocetak, Instant kraj, int mjesto, String status,
			Instant nastala, Instant rok) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at, expires_at,
					seat, price_cents)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, resurs.id(), klijent, utc(pocetak), utc(kraj), status, utc(nastala), utc(rok), mjesto,
				resurs.cijena());
		return id;
	}

	private void dogadjaj(UUID rezervacija, String od, String u, UUID akter, Instant kada) {
		jdbc.update("""
				insert into booking_events (id, booking_id, from_status, to_status, actor_id, created_at)
				values (?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), rezervacija, od, u, akter, utc(kada));
	}

	private UUID korisnik(DemoRacun racun, String lozinka, Instant sada) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into users (id, email, password_hash, display_name, created_at, email_verified_at)
				values (?, ?, ?, ?, ?, ?)
				""", id, racun.email(), passwordEncoder.encode(lozinka), racun.ime(), utc(sada), utc(sada));
		return id;
	}

	// Domena .test je rezervisana (RFC 2606) i nikad ne postoji, pa mail s dema ne stigne nikome
	private static String email(String ime) {
		String bezKvacica = ime.toLowerCase(Locale.ROOT).replace('č', 'c').replace('ć', 'c').replace('š', 's').replace('ž', 'z')
				.replace("đ", "dj");
		return bezKvacica.replace(' ', '.') + "@slotbook.test";
	}

	private UUID organizacija(String naziv, String slug, Instant sada) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into organizations (id, name, slug, timezone, created_at) values (?, ?, ?, ?, ?)", id,
				naziv, slug, ZONA.getId(), utc(sada));
		return id;
	}

	private void clan(UUID organizacija, UUID korisnik, String uloga, Instant sada) {
		jdbc.update("insert into organization_members (organization_id, user_id, role, joined_at) values (?, ?, ?, ?)",
				organizacija, korisnik, uloga, utc(sada));
	}

	private Resurs resurs(UUID organizacija, UUID provider, String naziv, String opis, int minuta, int mjesta,
			int cijena, boolean automatskaPotvrda, int satiZaOtkazivanje, Instant sada) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into resources (id, organization_id, provider_id, name, description, slot_minutes, capacity,
					price_cents, auto_confirm, cancel_notice_hours, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, organizacija, provider, naziv, opis, minuta, mjesta, cijena, automatskaPotvrda,
				satiZaOtkazivanje, utc(sada));
		return new Resurs(id, minuta, cijena);
	}

	private void radnoVrijeme(Resurs resurs, Set<DayOfWeek> dani, String od, String doVremena) {
		for (DayOfWeek dan : dani) {
			jdbc.update("""
					insert into availability_rules (id, resource_id, day_of_week, start_time, end_time)
					values (?, ?, ?, ?, ?)
					""", UUID.randomUUID(), resurs.id(), dan.getValue(), LocalTime.parse(od), LocalTime.parse(doVremena));
		}
	}

	// JDBC drajver za Postgres ne prima Instant, a OffsetDateTime u UTC-u ide u TIMESTAMPTZ bez pomjeranja
	private static OffsetDateTime utc(Instant trenutak) {
		return trenutak == null ? null : trenutak.atOffset(ZoneOffset.UTC);
	}

	private record Resurs(UUID id, int minuta, int cijena) {
	}

}
