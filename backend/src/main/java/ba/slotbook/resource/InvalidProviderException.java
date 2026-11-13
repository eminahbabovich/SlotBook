package ba.slotbook.resource;

public class InvalidProviderException extends RuntimeException {

	public InvalidProviderException() {
		super("Novi provider nije ADMIN ni PROVIDER u organizaciji resursa");
	}

}
