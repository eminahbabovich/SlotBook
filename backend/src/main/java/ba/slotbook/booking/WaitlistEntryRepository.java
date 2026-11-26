package ba.slotbook.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

	Optional<WaitlistEntry> findByResourceIdAndClientIdAndStartTimeAndStatusIn(UUID resourceId, UUID clientId,
			Instant startTime, Set<WaitlistStatus> statusi);

	// Ponuda koja još traje drži jedno mjesto za tog jednog klijenta. Istekla ga ne drži ni prije nego što je
	// posao obradi, pa kašnjenje posla ne blokira termin duže od roka.
	@Query("""
			select count(w) from WaitlistEntry w
			where w.resource.id = :resourceId and w.status = ba.slotbook.booking.WaitlistStatus.OFFERED
			and w.offeredUntil > :sada and w.client.id <> :clientId
			and w.startTime < :end and w.endTime > :start""")
	long countOffersToOthers(UUID resourceId, Instant start, Instant end, UUID clientId, Instant sada);

	@Modifying
	@Query(nativeQuery = true, value = """
			update waitlist_entries set status = 'BOOKED'
			where resource_id = :resourceId and client_id = :clientId and start_time = :start
			and status in ('WAITING', 'OFFERED')""")
	int markBooked(UUID resourceId, UUID clientId, Instant start);

	// Uklonjen član više ne čeka termine te organizacije
	@Modifying
	@Query(nativeQuery = true, value = """
			update waitlist_entries set status = 'CANCELLED'
			where client_id = :clientId and status in ('WAITING', 'OFFERED')
			and resource_id in (select id from resources where organization_id = :organizationId)""")
	int cancelAllInOrganization(UUID organizationId, UUID clientId);

	// Ponuda kojoj je prošao rok, i svaki živ upis za termin koji je već počeo. Vraća resurse kojima
	// je istekla ponuda, jer tamo se termin drugima opet prikazuje kao slobodan (SlotsChanged).
	// Bez @Modifying, jer naredba vraća redove, kao BookingRepository.expireAll.
	@Query(nativeQuery = true, value = """
			with istekli as (
				update waitlist_entries set status = 'EXPIRED'
				where (status = 'OFFERED' and offered_until <= :sada)
				or (status in ('WAITING', 'OFFERED') and start_time <= :sada)
				returning resource_id, offered_until)
			select distinct resource_id from istekli where offered_until is not null""")
	List<UUID> expireAll(Instant sada);

	// Prvi na listi za svaki termin u kojem ima mjesta koje ne drži ni rezervacija ni ponuda. Ovo je
	// samo prijedlog: posao pod lockom resursa ponovo provjeri. Po terminu jedna ponuda po prolazu, pa
	// kad se oslobode dva mjesta, drugo se nudi minutu kasnije.
	@Query(nativeQuery = true, value = """
			select distinct on (w.resource_id, w.start_time) w.id from waitlist_entries w
			join resources r on r.id = w.resource_id
			where w.status = 'WAITING' and w.start_time > :sada
			and (select count(*) from waitlist_entries o where o.resource_id = w.resource_id
			     and o.start_time = w.start_time and o.status = 'OFFERED')
			  + (select count(distinct b.seat) from bookings b where b.resource_id = w.resource_id
			     and b.status in ('PENDING', 'CONFIRMED')
			     and b.start_time < w.end_time and b.end_time > w.start_time) < r.capacity
			order by w.resource_id, w.start_time, w.created_at, w.id""")
	List<UUID> findFirstInLineForFreeSlots(Instant sada);

	// Uslov na status: ako se klijent u međuvremenu ispisao, ponuda ne ide
	@Modifying
	@Query(nativeQuery = true, value = """
			update waitlist_entries set status = 'OFFERED', offered_until = :rok
			where id = :id and status = 'WAITING'""")
	int offer(UUID id, Instant rok);

	// Ponude koje još traju, i tuđe: za kalendar, da termin ponuđen drugom ne izgleda slobodan
	@Query("""
			select w from WaitlistEntry w
			where w.resource.id = :resourceId and w.status = ba.slotbook.booking.WaitlistStatus.OFFERED
			and w.offeredUntil > :sada and w.startTime < :to and w.endTime > :from""")
	List<WaitlistEntry> findActiveOffers(UUID resourceId, Instant from, Instant to, Instant sada);

	@Query("""
			select w from WaitlistEntry w
			where w.resource.id = :resourceId and w.client.id = :clientId and w.status in :statusi
			and w.startTime < :to and w.endTime > :from""")
	List<WaitlistEntry> findLiveForClient(UUID resourceId, UUID clientId, Instant from, Instant to,
			Set<WaitlistStatus> statusi);

	// join fetch kao kod mojih rezervacija: naziv resursa, slug i zona u istom upitu (bez N+1).
	// countQuery bez fetcha, jer Hibernate ne zna brojati redove upita s join fetch.
	@Query(value = """
			select w from WaitlistEntry w join fetch w.resource r join fetch r.organization
			where w.client.id = :clientId and w.status in :statusi""", countQuery = """
			select count(w) from WaitlistEntry w where w.client.id = :clientId and w.status in :statusi""")
	Page<WaitlistEntry> findMine(UUID clientId, Set<WaitlistStatus> statusi, Pageable pageable);

	@Query("""
			select w from WaitlistEntry w join fetch w.resource r join fetch r.organization
			where w.id = :id""")
	Optional<WaitlistEntry> findWithResourceById(UUID id);

}
