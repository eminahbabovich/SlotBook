package ba.slotbook;

import org.springframework.boot.SpringApplication;

public class TestSlotbookApplication {

	public static void main(String[] args) {
		SpringApplication.from(SlotbookApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
