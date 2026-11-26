package ba.slotbook.resource;

// Pravila korištenja resursa. null znači da pravila nema. Važe za klijente, a ne za onoga ko vodi
// termine (BookingRules).
public record ResourceRules(Integer maxActivePerMember, Integer maxDaysAhead, Integer minNoticeMinutes) {

	public static final ResourceRules BEZ = new ResourceRules(null, null, null);

}
