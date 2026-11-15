package ba.slotbook.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.availability.SlotService;
import ba.slotbook.booking.BookingStatus;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.organization.OrganizationRole;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceRepository;

@Service
public class StatsService {

	// Cijela godina, i prestupna. Duži period bi računao ponuđene termine za previše dana odjednom.
	static final int NAJVISE_DANA = 366;

	private final StatsRepository stats;
	private final ResourceRepository resources;
	private final OrganizationService organizationService;
	private final SlotService slotService;

	public StatsService(StatsRepository stats, ResourceRepository resources, OrganizationService organizationService,
			SlotService slotService) {
		this.stats = stats;
		this.resources = resources;
		this.organizationService = organizationService;
		this.slotService = slotService;
	}

	@Transactional(readOnly = true)
	public StatsResponse stats(UUID organizationId, UUID userId, LocalDate from, LocalDate to) {
		provjeri(from, to);
		ZoneId zona = ZoneId.of(organizationService.getReference(organizationId).getTimezone());
		// Ponoć u zoni organizacije, s pomakom koji tog dana važi: 1.10.2035. je +02, a 1.11. +01
		Instant od = from.atStartOfDay(zona).toInstant();
		Instant doo = to.atStartOfDay(zona).toInstant();

		// Cijelu organizaciju vidi samo admin. Svi ostali idu kroz upite za resurse koje vode, pa
		// klijent koji bi greškom prošao @PreAuthorize dobije prazne brojke, a ne tuđe podatke.
		boolean admin = organizationService.findRole(organizationId, userId)
				.filter(uloga -> uloga == OrganizationRole.ADMIN)
				.isPresent();

		List<StatusCount> statusi = admin ? stats.countByStatus(organizationId, od, doo)
				: stats.countByStatusForProvider(organizationId, userId, od, doo);
		List<DayHourCount> danISat = admin ? stats.countByDayAndHour(organizationId, od, doo)
				: stats.countByDayAndHourForProvider(organizationId, userId, od, doo);
		List<BookedMinutes> zauzeto = admin ? stats.bookedMinutes(organizationId, od, doo)
				: stats.bookedMinutesForProvider(organizationId, userId, od, doo);
		List<Resource> resursi = admin ? resources.findByOrganizationIdOrderByNameAscIdAsc(organizationId)
				: resources.findByOrganizationIdAndProviderIdOrderByNameAscIdAsc(organizationId, userId);

		return new StatsResponse(from, to, zona.getId(), saNulama(statusi), danISat,
				popunjenost(resursi, zauzeto, from, to));
	}

	// GROUP BY ne vraća status bez ijedne rezervacije, a frontend treba svih sedam. EnumMap drži
	// redoslijed iz enuma, pa i JSON uvijek ima isti redoslijed.
	private static Map<BookingStatus, Long> saNulama(List<StatusCount> statusi) {
		Map<BookingStatus, Long> poStatusu = new EnumMap<>(BookingStatus.class);
		for (BookingStatus status : BookingStatus.values()) {
			poStatusu.put(status, 0L);
		}
		statusi.forEach(red -> poStatusu.put(red.status(), red.count()));
		return poStatusu;
	}

	// I resurs bez ijedne rezervacije, jer je prazna sala najkorisniji podatak
	private List<ResourceOccupancy> popunjenost(List<Resource> resursi, List<BookedMinutes> zauzeto, LocalDate from,
			LocalDate to) {
		Map<UUID, Long> minutePoResursu = zauzeto.stream()
				.collect(Collectors.toMap(BookedMinutes::resourceId, BookedMinutes::minutes));
		return resursi.stream()
				.map(resurs -> new ResourceOccupancy(resurs.getId(), resurs.getName(),
						minutePoResursu.getOrDefault(resurs.getId(), 0L),
						slotService.offeredMinutes(resurs.getId(), from, to)))
				.toList();
	}

	static void provjeri(LocalDate from, LocalDate to) {
		if (!from.isBefore(to)) {
			throw new InvalidFieldException("to",
					"Datum do mora biti poslije datuma od. Za jedan dan pošalji npr. from=2026-11-16 i to=2026-11-17.");
		}
		if (ChronoUnit.DAYS.between(from, to) > NAJVISE_DANA) {
			throw new InvalidFieldException("to",
					"Period može biti najviše " + NAJVISE_DANA + " dana. Za duži period pošalji više zahtjeva.");
		}
	}

}
