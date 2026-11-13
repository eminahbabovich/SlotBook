package ba.slotbook.resource;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException() {
		super("Resurs ne postoji u ovoj organizaciji");
	}

}
