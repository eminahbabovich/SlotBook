package ba.slotbook.booking;

// Link koji ide u QR kod. Otvara stranicu za providera, a token je iza #, kao kod pozivnice: browser
// ga ne šalje serveru frontenda i ostaje u linku i kad se provider prvo mora prijaviti.
public record CheckInLinkResponse(String url) {
}
