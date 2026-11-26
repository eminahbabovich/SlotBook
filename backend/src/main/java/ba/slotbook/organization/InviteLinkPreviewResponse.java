package ba.slotbook.organization;

// Šta vidi neko ko otvori link prije prijave: ime organizacije i da li link još radi. Bez broja
// korištenja i bez ičega o članovima.
public record InviteLinkPreviewResponse(String organizationName, String slug, boolean usable) {
}
