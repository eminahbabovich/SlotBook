package ba.slotbook.user;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// Lozinka koja se ne pogađa lako. Nema pravila "veliko slovo, broj i znak": po NIST 800-63B ono tjera
// ljude na "Lozinka1!", a ne na jaču lozinku. Umjesto toga dužina i odbijanje onoga što napadač prvo
// proba: česte lozinke, nizovi s tastature i isti znak više puta. Vidi docs/ODLUKE.md.
public final class ProvjeraLozinke {

	public static final int NAJMANJE_ZNAKOVA = 10;

	// Redovi tastature (QWERTZ i QWERTY), abeceda i cifre. Lozinka koja je cijela komad jednog od
	// ovih nizova, naprijed ili nazad, pada: "12345678910", "qwertzuiop", "asdfghjklč".
	private static final List<String> NIZOVI = List.of("01234567890123456789",
			"abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz",
			"qwertzuiopšđasdfghjklčćžyxcvbnm", "qwertyuiopasdfghjklzxcvbnm", "1qaz2wsx3edc4rfv5tgb6zhn7ujm8ik9ol0p");

	// Učita se jednom, kad se klasa prvi put koristi
	private static final Set<String> CESTE = ucitajCeste();

	public static boolean sadrziEmail(String lozinka, String email) {
		String ime = email.substring(0, Math.max(0, email.indexOf('@'))).toLowerCase(Locale.ROOT);
		return ime.length() >= 4 && lozinka.toLowerCase(Locale.ROOT).contains(ime);
	}

	// null znači da je lozinka u redu, inače poruka za korisnika
	public static String problem(String lozinka) {
		if (lozinka.codePointCount(0, lozinka.length()) < NAJMANJE_ZNAKOVA) {
			return "Lozinka treba imati najmanje " + NAJMANJE_ZNAKOVA + " znakova.";
		}
		String mala = lozinka.toLowerCase(Locale.ROOT);
		// "aaaaaaaaaa" i "abababababab": manje od četiri različita znaka
		if (mala.codePoints().distinct().count() < 4) {
			return "Lozinka ne smije biti jedan ili dva znaka koji se ponavljaju.";
		}
		for (String niz : NIZOVI) {
			if (niz.contains(mala) || new StringBuilder(niz).reverse().toString().contains(mala)) {
				return "Lozinka ne smije biti niz s tastature ili redom slova i cifara.";
			}
		}
		// "lozinka2024!" je "lozinka" s brojem na kraju, a napadač tu varijantu proba odmah poslije osnovne
		String osnova = mala.replaceAll("[^\\p{L}]+$", "").replaceAll("^[^\\p{L}]+", "");
		if (CESTE.contains(mala) || CESTE.contains(osnova) || CESTE.contains(ponovljenaRijec(osnova))) {
			return "Ovu lozinku ljudi često koriste, pa se lako pogodi. Probaj tri riječi koje ti nešto znače i broj.";
		}
		return null;
	}

	// "testtest" i "lozinkalozinka" su česta riječ dvaput. Vraća riječ koja se ponavlja ili cijeli tekst.
	private static String ponovljenaRijec(String tekst) {
		for (int duzina = 1; duzina <= tekst.length() / 2; duzina++) {
			if (tekst.length() % duzina == 0
					&& tekst.substring(0, duzina).repeat(tekst.length() / duzina).equals(tekst)) {
				return tekst.substring(0, duzina);
			}
		}
		return tekst;
	}

	private static Set<String> ucitajCeste() {
		try (InputStream ulaz = ProvjeraLozinke.class.getResourceAsStream("/lozinke/ceste.txt")) {
			return new String(ulaz.readAllBytes(), StandardCharsets.UTF_8).lines()
					.map(String::strip)
					.filter(red -> !red.isEmpty() && !red.startsWith("#"))
					.map(red -> red.toLowerCase(Locale.ROOT))
					.collect(Collectors.toUnmodifiableSet());
		} catch (IOException | NullPointerException e) {
			throw new IllegalStateException("Fali lista čestih lozinaka /lozinke/ceste.txt", e);
		}
	}

	private ProvjeraLozinke() {
	}

}
