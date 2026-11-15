package ba.slotbook.stats;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import ba.slotbook.booking.Booking;

// Repository umjesto JpaRepository: statistika samo čita, pa nema ni save ni delete
interface StatsRepository extends Repository<Booking, UUID> {

	// Period se gleda po početku termina, ne po trenutku rezervacije: "novembar" je ono što se
	// dešavalo u čitaonici u novembru. Granica je [od, do), kao kod termina, pa se dva susjedna
	// perioda ne preklapaju i nijedna rezervacija se ne broji dvaput.
	@Query("""
			select new ba.slotbook.stats.StatusCount(b.status, count(b))
			from Booking b
			where b.resource.organization.id = :organizationId
				and b.startTime >= :periodOd and b.startTime < :periodDo
			group by b.status""")
	List<StatusCount> countByStatus(UUID organizationId, Instant periodOd, Instant periodDo);

	// Provider vidi samo resurse koje vodi. Posebna metoda, a ne providerId koji smije biti null i
	// tada znači "sve": tako greška u servisu ne može provideru otvoriti cijelu organizaciju.
	// Organizacija ostaje u uslovu jer isti korisnik može voditi resurse u dvije organizacije.
	@Query("""
			select new ba.slotbook.stats.StatusCount(b.status, count(b))
			from Booking b
			where b.resource.organization.id = :organizationId
				and b.resource.provider.id = :providerId
				and b.startTime >= :periodOd and b.startTime < :periodDo
			group by b.status""")
	List<StatusCount> countByStatusForProvider(UUID organizationId, UUID providerId, Instant periodOd,
			Instant periodDo);

	// Native, jer JPQL nema AT TIME ZONE. Dan i sat se izvlače u zoni organizacije, inače bi termin
	// u 9:00 ljeti pao u 7 po UTC-u, a zimi u 8, i ista navika bi se raspala na dva sata. Broje se svi
	// statusi: i otkazan termin je neko htio. CAST jer extract vraća numeric, a record traži int.
	@Query(nativeQuery = true, value = """
			SELECT CAST(extract(isodow FROM b.start_time AT TIME ZONE o.timezone) AS integer) AS dan,
				CAST(extract(hour FROM b.start_time AT TIME ZONE o.timezone) AS integer) AS sat,
				count(*) AS broj
			FROM bookings b
			JOIN resources r ON r.id = b.resource_id
			JOIN organizations o ON o.id = r.organization_id
			WHERE r.organization_id = :organizationId
				AND b.start_time >= :periodOd AND b.start_time < :periodDo
			GROUP BY dan, sat
			ORDER BY dan, sat""")
	List<DayHourCount> countByDayAndHour(UUID organizationId, Instant periodOd, Instant periodDo);

	// Isto, samo resursi koje provider vodi, iz istog razloga kao countByStatusForProvider
	@Query(nativeQuery = true, value = """
			SELECT CAST(extract(isodow FROM b.start_time AT TIME ZONE o.timezone) AS integer) AS dan,
				CAST(extract(hour FROM b.start_time AT TIME ZONE o.timezone) AS integer) AS sat,
				count(*) AS broj
			FROM bookings b
			JOIN resources r ON r.id = b.resource_id
			JOIN organizations o ON o.id = r.organization_id
			WHERE r.organization_id = :organizationId
				AND r.provider_id = :providerId
				AND b.start_time >= :periodOd AND b.start_time < :periodDo
			GROUP BY dan, sat
			ORDER BY dan, sat""")
	List<DayHourCount> countByDayAndHourForProvider(UUID organizationId, UUID providerId, Instant periodOd,
			Instant periodDo);

	// Brojilac popunjenosti. Zauzeto je ono što je držalo termin do kraja: i nedolazak, jer taj termin
	// niko drugi nije mogao uzeti. PENDING ne ulazi, jer prošao termin ne smije ostati na čekanju.
	// Minute iz razlike trenutaka, pa termin preko noći pomjeranja sata traje stvarno vrijeme.
	@Query(nativeQuery = true, value = """
			SELECT b.resource_id, CAST(sum(extract(epoch FROM b.end_time - b.start_time)) / 60 AS bigint)
			FROM bookings b
			JOIN resources r ON r.id = b.resource_id
			WHERE r.organization_id = :organizationId
				AND b.status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW')
				AND b.start_time >= :periodOd AND b.start_time < :periodDo
			GROUP BY b.resource_id""")
	List<BookedMinutes> bookedMinutes(UUID organizationId, Instant periodOd, Instant periodDo);

	@Query(nativeQuery = true, value = """
			SELECT b.resource_id, CAST(sum(extract(epoch FROM b.end_time - b.start_time)) / 60 AS bigint)
			FROM bookings b
			JOIN resources r ON r.id = b.resource_id
			WHERE r.organization_id = :organizationId
				AND r.provider_id = :providerId
				AND b.status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW')
				AND b.start_time >= :periodOd AND b.start_time < :periodDo
			GROUP BY b.resource_id""")
	List<BookedMinutes> bookedMinutesForProvider(UUID organizationId, UUID providerId, Instant periodOd,
			Instant periodDo);

}
