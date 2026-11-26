package ba.slotbook.notification;

// Ono što service worker dobije u push događaju: naslov, jedna rečenica i stranica koja se otvara na klik
record PushMessage(String title, String body, String url) {
}
