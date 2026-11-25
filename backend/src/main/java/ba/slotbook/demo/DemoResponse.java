package ba.slotbook.demo;

import java.util.List;

public record DemoResponse(String lozinka, List<DemoRacun> racuni) {
}
