package ba.slotbook.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
		@NotBlank(message = "Napiši poruku asistentu.")
		@Size(max = 1000, message = "Poruka smije imati najviše 1000 znakova.")
		String text) {
}
