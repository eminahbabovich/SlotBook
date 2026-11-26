package ba.slotbook.organization;

import java.util.List;

public class MemberLeadsResourcesException extends RuntimeException {

	private final List<String> resursi;

	public MemberLeadsResourcesException(List<String> resursi) {
		super("Član vodi resurse: " + resursi.size());
		this.resursi = List.copyOf(resursi);
	}

	public List<String> getResursi() {
		return resursi;
	}

}
