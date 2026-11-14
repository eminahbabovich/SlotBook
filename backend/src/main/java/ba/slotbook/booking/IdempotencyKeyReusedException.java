package ba.slotbook.booking;

public class IdempotencyKeyReusedException extends RuntimeException {

	public IdempotencyKeyReusedException() {
		super("Idempotency-Key je već iskorišten za drugu rezervaciju");
	}

}
