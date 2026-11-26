package ba.slotbook.organization;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// "limit nedolazaka u windowDays dana daje blokadu od blockDays dana". Sva tri ili nijedan, kao CHECK u V43.
public record NoShowRules(
		@Min(value = 1, message = "Najmanje jedan nedolazak.")
		@Max(value = 20, message = "Najviše 20 nedolazaka.")
		Integer limit,
		@Min(value = 1, message = "Period je najmanje jedan dan.")
		@Max(value = 365, message = "Period je najviše 365 dana.")
		Integer windowDays,
		@Min(value = 1, message = "Blokada traje najmanje jedan dan.")
		@Max(value = 365, message = "Blokada traje najviše 365 dana.")
		Integer blockDays) {

	public static final NoShowRules BEZ = new NoShowRules(null, null, null);

	public boolean postoji() {
		return limit != null;
	}

	// Polje greške je "limit", jer je to prvo polje u formi
	@AssertTrue(message = "Upiši sva tri broja (nedolasci, period i blokada) ili nijedan.")
	boolean isLimit() {
		return (limit == null) == (windowDays == null) && (windowDays == null) == (blockDays == null);
	}

}
