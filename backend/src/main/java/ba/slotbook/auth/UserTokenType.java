package ba.slotbook.auth;

// Javni, jer GlobalExceptionHandler po njemu bira poruku za link koji ne važi
public enum UserTokenType {
	EMAIL_VERIFY, PASSWORD_RESET
}
