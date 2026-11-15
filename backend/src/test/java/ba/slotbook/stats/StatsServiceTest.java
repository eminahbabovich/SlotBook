package ba.slotbook.stats;

import static ba.slotbook.booking.BookingStatus.CANCELLED;
import static ba.slotbook.booking.BookingStatus.COMPLETED;
import static ba.slotbook.booking.BookingStatus.CONFIRMED;
import static ba.slotbook.booking.BookingStatus.EXPIRED;
import static ba.slotbook.booking.BookingStatus.NO_SHOW;
import static ba.slotbook.booking.BookingStatus.PENDING;
import static ba.slotbook.booking.BookingStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ba.slotbook.availability.SlotService;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.organization.Organization;
import ba.slotbook.organization.OrganizationRole;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceRepository;

class StatsServiceTest {

	// Oktobar 2035. počinje po ljetnom (+02), a završava po zimskom vremenu (+01). Stubovi su na
	// tačno ovim trenucima, pa pogrešna zona znači da nijedan stub ne odgovori.
	private static final LocalDate OKTOBAR = LocalDate.parse("2035-10-01");
	private static final LocalDate NOVEMBAR = LocalDate.parse("2035-11-01");
	private static final Instant OKTOBAR_OD = Instant.parse("2035-09-30T22:00:00Z");
	private static final Instant OKTOBAR_DO = Instant.parse("2035-10-31T23:00:00Z");

	private final StatsRepository stats = mock(StatsRepository.class);
	private final ResourceRepository resources = mock(ResourceRepository.class);
	private final OrganizationService organizationService = mock(OrganizationService.class);
	private final SlotService slotService = mock(SlotService.class);
	private final StatsService service = new StatsService(stats, resources, organizationService, slotService);

	private final UUID citaonica = UUID.randomUUID();
	private final UUID selma = UUID.randomUUID();

	@BeforeEach
	void citaonicaUSarajevu() {
		Organization organizacija = mock(Organization.class);
		when(organizacija.getTimezone()).thenReturn("Europe/Sarajevo");
		when(organizationService.getReference(citaonica)).thenReturn(organizacija);
	}

	@Test
	void adminDobijaCijeluOrganizacijuSNulamaZaStatuseIResurseBezRezervacija() {
		uloga(OrganizationRole.ADMIN);
		UUID stoBr1 = UUID.randomUUID();
		Resource sto1 = resurs(stoBr1, "Sto br. 1");
		UUID stoBr2 = UUID.randomUUID();
		Resource sto2 = resurs(stoBr2, "Sto br. 2");
		when(resources.findByOrganizationIdOrderByNameAscIdAsc(citaonica)).thenReturn(List.of(sto1, sto2));
		when(stats.countByStatus(citaonica, OKTOBAR_OD, OKTOBAR_DO))
				.thenReturn(List.of(new StatusCount(CONFIRMED, 4), new StatusCount(NO_SHOW, 1)));
		when(stats.countByDayAndHour(citaonica, OKTOBAR_OD, OKTOBAR_DO)).thenReturn(List.of(new DayHourCount(3, 9, 5)));
		when(stats.bookedMinutes(citaonica, OKTOBAR_OD, OKTOBAR_DO))
				.thenReturn(List.of(new BookedMinutes(stoBr1, 300)));
		when(slotService.offeredMinutes(stoBr1, OKTOBAR, NOVEMBAR)).thenReturn(1200L);
		when(slotService.offeredMinutes(stoBr2, OKTOBAR, NOVEMBAR)).thenReturn(600L);

		StatsResponse odgovor = service.stats(citaonica, selma, OKTOBAR, NOVEMBAR);

		assertThat(odgovor.timezone()).isEqualTo("Europe/Sarajevo");
		assertThat(odgovor.byStatus()).containsExactly(entry(PENDING, 0L), entry(CONFIRMED, 4L), entry(REJECTED, 0L),
				entry(EXPIRED, 0L), entry(CANCELLED, 0L), entry(COMPLETED, 0L), entry(NO_SHOW, 1L));
		assertThat(odgovor.byDayAndHour()).containsExactly(new DayHourCount(3, 9, 5));
		assertThat(odgovor.occupancy()).containsExactly(
				new ResourceOccupancy(stoBr1, "Sto br. 1", 300, 1200),
				new ResourceOccupancy(stoBr2, "Sto br. 2", 0, 600));
	}

	@Test
	void providerIdeSamoKrozUpiteZaResurseKojeVodi() {
		uloga(OrganizationRole.PROVIDER);
		UUID stoBr1 = UUID.randomUUID();
		Resource sto1 = resurs(stoBr1, "Sto br. 1");
		when(resources.findByOrganizationIdAndProviderIdOrderByNameAscIdAsc(citaonica, selma))
				.thenReturn(List.of(sto1));
		when(stats.countByStatusForProvider(citaonica, selma, OKTOBAR_OD, OKTOBAR_DO))
				.thenReturn(List.of(new StatusCount(CONFIRMED, 2)));
		when(stats.bookedMinutesForProvider(citaonica, selma, OKTOBAR_OD, OKTOBAR_DO))
				.thenReturn(List.of(new BookedMinutes(stoBr1, 120)));
		when(slotService.offeredMinutes(stoBr1, OKTOBAR, NOVEMBAR)).thenReturn(600L);

		StatsResponse odgovor = service.stats(citaonica, selma, OKTOBAR, NOVEMBAR);

		assertThat(odgovor.byStatus()).containsEntry(CONFIRMED, 2L);
		assertThat(odgovor.occupancy()).containsExactly(new ResourceOccupancy(stoBr1, "Sto br. 1", 120, 600));
		nikadCijelaOrganizacija();
	}

	// Klijent ovdje ne bi smio ni stići, to čuva @PreAuthorize. Ako ipak stigne, ide kroz upite za
	// resurse koje vodi, a to je nijedan.
	@Test
	void klijentNikadNeDobijaUpiteZaCijeluOrganizaciju() {
		uloga(OrganizationRole.CLIENT);

		service.stats(citaonica, selma, OKTOBAR, NOVEMBAR);

		nikadCijelaOrganizacija();
	}

	@Test
	void periodJeNajviseGodinuDanaIKrajMoraBitiPoslijePocetka() {
		assertThatCode(() -> StatsService.provjeri(OKTOBAR, OKTOBAR.plusDays(366))).doesNotThrowAnyException();
		assertThatThrownBy(() -> StatsService.provjeri(OKTOBAR, OKTOBAR.plusDays(367)))
				.isInstanceOf(InvalidFieldException.class)
				.hasFieldOrPropertyWithValue("polje", "to");
		assertThatThrownBy(() -> StatsService.provjeri(OKTOBAR, OKTOBAR)).isInstanceOf(InvalidFieldException.class);
	}

	private void uloga(OrganizationRole uloga) {
		when(organizationService.findRole(citaonica, selma)).thenReturn(Optional.of(uloga));
	}

	private Resource resurs(UUID id, String naziv) {
		Resource resurs = mock(Resource.class);
		when(resurs.getId()).thenReturn(id);
		when(resurs.getName()).thenReturn(naziv);
		return resurs;
	}

	private void nikadCijelaOrganizacija() {
		verify(stats, never()).countByStatus(any(), any(), any());
		verify(stats, never()).countByDayAndHour(any(), any(), any());
		verify(stats, never()).bookedMinutes(any(), any(), any());
		verify(resources, never()).findByOrganizationIdOrderByNameAscIdAsc(any());
	}

}
