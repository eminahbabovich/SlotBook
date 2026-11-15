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

}
