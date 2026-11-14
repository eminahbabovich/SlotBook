package ba.slotbook.user;

import java.time.Clock;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	public UserResponse getById(UUID id) {
		return users.findById(id).map(UserResponse::from).orElseThrow();
	}

	// Ne pitamo prvo "postoji li email": dva istovremena zahtjeva bi oba dobila "ne postoji".
	// Odluku donosi UNIQUE u bazi, a mi samo prevodimo njenu grešku.
	// Transakcija je tu da mail za potvrdu, koji upisuje pozivalac, nastane zajedno s nalogom ili nikako
	@Transactional
	public UserResponse register(String email, String password, String displayName) {
		User user = new User(email, passwordEncoder.encode(password), displayName, clock.instant());
		try {
			return UserResponse.from(users.saveAndFlush(user));
		} catch (DataIntegrityViolationException e) {
			throw new EmailAlreadyUsedException();
		}
	}

}
