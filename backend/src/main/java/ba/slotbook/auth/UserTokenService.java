package ba.slotbook.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.auth.UserTokenException.Reason;
import ba.slotbook.user.User;

// Zajedničko za linkove iz maila (potvrda emaila, nova lozinka): token postoji samo u linku,
// baza čuva SHA-256, a iskorištavanje je jedan UPDATE koji uspije samo prvi put.
// MANDATORY: token mora nastati i biti iskorišten u istoj transakciji kao ono zbog čega postoji.
@Service
class UserTokenService {

	private final UserTokenRepository tokens;
	private final Clock clock;

	UserTokenService(UserTokenRepository tokens, Clock clock) {
		this.tokens = tokens;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public String issue(User user, UserTokenType type, Duration traje) {
		String token = RandomToken.generate();
		Instant sada = clock.instant();
		tokens.save(new UserToken(user, type, RandomToken.sha256(token), sada, sada.plus(traje)));
		return token;
	}

	// Tip je dio upita: token iz maila za potvrdu emaila ne može postaviti novu lozinku
	@Transactional(propagation = Propagation.MANDATORY)
	public User use(String token, UserTokenType type) {
		UserToken zapis = tokens.findByTokenHashAndType(RandomToken.sha256(token), type)
				.orElseThrow(() -> new UserTokenException(type, Reason.NOT_FOUND));
		Instant sada = clock.instant();
		if (zapis.isExpired(sada)) {
			throw new UserTokenException(type, Reason.EXPIRED);
		}
		if (tokens.markUsed(zapis.getId(), sada) == 0) {
			throw new UserTokenException(type, Reason.ALREADY_USED);
		}
		return zapis.getUser();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void useAll(User user, UserTokenType type) {
		tokens.markAllUsed(user, type, clock.instant());
	}

}
