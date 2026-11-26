package ba.slotbook.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebPushClientTest {

	// Bez ključeva u okruženju aplikacija mora raditi, samo bez pusha
	@Test
	void bezKljucevaPushJeIskljucen() {
		WebPushClient klijent = new WebPushClient("", " ", "mailto:obavijesti@slotbook.local");

		assertThat(klijent.ukljucen()).isFalse();
		assertThat(klijent.javniKljuc()).isNull();
	}

	@Test
	void neispravanPrivatniKljucObaraPokretanjeSJasnomPorukom() {
		assertThatThrownBy(() -> new WebPushClient("BAAA", "ovo-nije-kljuc!", "mailto:x@slotbook.local"))
				.hasMessageContaining("VAPID");
	}

	// Server šalje samo na poznate push servise preko HTTPS-a, inače bi endpoint iz browsera mogao
	// natjerati server da gađa adresu u svojoj mreži
	@Test
	void primaSamoHttpsAdresePoznatihPushServisa() {
		assertThat(WebPushClient.poznatServis("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
		assertThat(WebPushClient.poznatServis("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
		assertThat(WebPushClient.poznatServis("https://web.push.apple.com/QGx")).isTrue();
		assertThat(WebPushClient.poznatServis("https://wns2-db5p.notify.windows.com/w/?token=abc")).isTrue();

		assertThat(WebPushClient.poznatServis("http://fcm.googleapis.com/fcm/send/abc")).isFalse();
		assertThat(WebPushClient.poznatServis("https://fcm.googleapis.com.zlo.ba/abc")).isFalse();
		assertThat(WebPushClient.poznatServis("https://169.254.169.254/latest/meta-data")).isFalse();
		assertThat(WebPushClient.poznatServis("https://localhost:8080/api/admin")).isFalse();
		assertThat(WebPushClient.poznatServis("nije adresa")).isFalse();
	}

}
