package ba.slotbook.account;

import java.time.Duration;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ba.slotbook.auth.LoginLockService;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.ratelimit.TooManyAttemptsException;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Service
public class AccountDeletionService {

	private final UserRepository users;
	private final LoginLockService zakljucavanje;
	private final PasswordEncoder passwordEncoder;
	private final AccountAnonymization anonymization;

	public AccountDeletionService(UserRepository users, LoginLockService zakljucavanje,
			PasswordEncoder passwordEncoder, AccountAnonymization anonymization) {
		this.users = users;
		this.zakljucavanje = zakljucavanje;
		this.passwordEncoder = passwordEncoder;
		this.anonymization = anonymization;
	}

	// Bez @Transactional, kao prijava: pogrešna lozinka se upiše u svojoj transakciji, pa je izuzetak
	// ispod ne poništi. Lozinka se traži zato što access token neko može ukrasti, a brisanje se ne vraća.
	public void delete(UUID userId, String password) {
		User user = users.findById(userId).orElseThrow();
		Duration zakljucano = zakljucavanje.lockedFor(user.getEmail());
		if (!zakljucano.isZero()) {
			throw new TooManyAttemptsException(TooManyAttemptsException.Razlog.NALOG, zakljucano);
		}
		// 400 na polju, a ne 401: frontend na 401 osvježava token, a ovdje je sesija ispravna
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			zakljucavanje.recordFailure(user.getEmail());
			throw new InvalidFieldException("password", "Lozinka nije tačna.");
		}
		anonymization.anonymize(userId, user.getEmail());
	}

}
