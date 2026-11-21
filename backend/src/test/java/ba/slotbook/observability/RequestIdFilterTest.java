package ba.slotbook.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

	private final RequestIdFilter filter = new RequestIdFilter();

	@Test
	void logTokomZahtjevaImaIstiIdKaoOdgovor() throws Exception {
		AtomicReference<String> uLogu = new AtomicReference<>();
		MockHttpServletResponse odgovor = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest(), odgovor, (z, o) -> uLogu.set(MDC.get("requestId")));

		assertThat(uLogu.get()).isNotBlank().isEqualTo(odgovor.getHeader("X-Request-Id"));
	}

	// Niti se ponovo koriste, pa bi ostavljen ID završio u logu sljedećeg zahtjeva na toj niti
	@Test
	void poslijeZahtjevaIdaViseNemaNiKadaZahtjevPadne() {
		assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (z, o) -> {
			throw new IllegalStateException("pad u kontroleru");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(MDC.get("requestId")).isNull();
	}

	// ID iz zahtjeva bi napadač mogao iskoristiti da u naš log upiše šta hoće
	@Test
	void idKlijentaSeNePrihvataISvakiZahtjevDobijaNovi() throws Exception {
		MockHttpServletRequest zahtjev = new MockHttpServletRequest();
		zahtjev.addHeader("X-Request-Id", "lazni\nERROR upad");
		MockHttpServletResponse prvi = new MockHttpServletResponse();
		MockHttpServletResponse drugi = new MockHttpServletResponse();

		filter.doFilter(zahtjev, prvi, (z, o) -> {});
		filter.doFilter(new MockHttpServletRequest(), drugi, (z, o) -> {});

		assertThat(prvi.getHeader("X-Request-Id")).doesNotContain("lazni").isNotEqualTo(drugi.getHeader("X-Request-Id"));
	}

}
