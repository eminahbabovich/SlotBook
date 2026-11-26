package ba.slotbook.screen;

// Token ne postoji, ekran je ugašen ili ga je zamijenio novi. Klijent ne treba znati koje od toga.
public class DoorScreenNotFoundException extends RuntimeException {

	public DoorScreenNotFoundException() {
		super("Ekran ne postoji ili je ugašen");
	}

}
