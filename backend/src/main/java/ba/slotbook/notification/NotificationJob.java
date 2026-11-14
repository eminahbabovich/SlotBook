package ba.slotbook.notification;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// U testovima je isključen (src/test/resources/application-default.properties), da testovi ne
// šalju mailove sami. Test koji provjerava slanje zove NotificationSender direktno.
@Component
@ConditionalOnBooleanProperty(name = "slotbook.notifications.sending", matchIfMissing = true)
class NotificationJob {

	// Da jedan krug ne traje unedogled kad se nakupi mnogo obavijesti. Ostatak ide u sljedećem.
	private static final int NAJVISE_PO_KRUGU = 50;

	private final NotificationSender sender;

	NotificationJob(NotificationSender sender) {
		this.sender = sender;
	}

	@Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
	void posalji() {
		int obradjeno = 0;
		while (obradjeno < NAJVISE_PO_KRUGU && sender.sendNext()) {
			obradjeno++;
		}
	}

}
