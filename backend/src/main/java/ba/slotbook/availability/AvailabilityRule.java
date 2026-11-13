package ba.slotbook.availability;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import ba.slotbook.resource.Resource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Jedan interval radnog vremena u sedmici, npr. ponedjeljak 08:00-12:00. Vrijeme je lokalno
// u zoni organizacije (LocalTime), a ne Instant, jer pravilo važi i zimi i ljeti.
@Entity
@Table(name = "availability_rules")
public class AvailabilityRule {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "resource_id", updatable = false)
	private Resource resource;

	// Broj po ISO (1 = ponedjeljak), ne EnumType.ORDINAL, koji bi ponedjeljak upisao kao 0
	@Column(name = "day_of_week", nullable = false)
	private int dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	protected AvailabilityRule() {
	}

	AvailabilityRule(Resource resource, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
		this.resource = resource;
		this.dayOfWeek = dayOfWeek.getValue();
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public DayOfWeek getDayOfWeek() {
		return DayOfWeek.of(dayOfWeek);
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

}
