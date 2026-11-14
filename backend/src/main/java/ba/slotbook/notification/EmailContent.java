package ba.slotbook.notification;

// ics je prilog za kalendar, samo kod potvrde rezervacije. Ostali mailovi ga nemaju.
public record EmailContent(String subject, String body, String ics) {

	public EmailContent(String subject, String body) {
		this(subject, body, null);
	}

}
