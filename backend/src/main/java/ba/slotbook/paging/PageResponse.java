package ba.slotbook.paging;

import java.util.List;

import org.springframework.data.domain.Page;

// Vlastiti oblik stranice umjesto Page iz Spring Data. Page se serijalizuje sa svim unutrašnjim
// poljima (pageable, sort...), a Spring Data ne garantuje da će taj JSON ostati isti među verzijama.
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> from(Page<T> stranica) {
		return new PageResponse<>(stranica.getContent(), stranica.getNumber(), stranica.getSize(),
				stranica.getTotalElements(), stranica.getTotalPages());
	}

}
