package ba.slotbook.notification;

// enabled je false kad server nema VAPID ključeve: tada frontend ne nudi uključivanje
public record PushKeyResponse(boolean enabled, String publicKey) {
}
