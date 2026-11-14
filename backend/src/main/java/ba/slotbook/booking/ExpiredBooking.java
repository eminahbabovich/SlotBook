package ba.slotbook.booking;

import java.util.UUID;

// Red iz RETURNING kod isteka: koja rezervacija je istekla i čiji je klijent, da dobije obavijest
interface ExpiredBooking {

	UUID getId();

	UUID getClientId();

}
