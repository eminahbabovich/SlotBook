package ba.slotbook.booking;

import java.time.Instant;

public class CancelDeadlinePassedException extends RuntimeException {

	private final Instant deadline;
	private final int noticeHours;

	public CancelDeadlinePassedException(Instant deadline, int noticeHours) {
		super("Rok za otkazivanje je bio " + deadline);
		this.deadline = deadline;
		this.noticeHours = noticeHours;
	}

	public Instant getDeadline() {
		return deadline;
	}

	public int getNoticeHours() {
		return noticeHours;
	}

}
