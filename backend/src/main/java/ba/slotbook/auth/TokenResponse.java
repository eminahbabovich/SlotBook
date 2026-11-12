package ba.slotbook.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
