package ba.slotbook.account;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
public class AccountDeletionController {

	public record DeleteAccountRequest(@NotBlank String password) {
	}

	private final AccountDeletionService deletionService;

	public AccountDeletionController(AccountDeletionService deletionService) {
		this.deletionService = deletionService;
	}

	// Korisnik iz tokena: tuđi nalog se ne može ni navesti
	@DeleteMapping("/api/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt token, @Valid @RequestBody DeleteAccountRequest request) {
		deletionService.delete(UUID.fromString(token.getSubject()), request.password());
	}

}
