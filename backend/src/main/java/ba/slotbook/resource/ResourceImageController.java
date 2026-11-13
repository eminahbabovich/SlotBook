package ba.slotbook.resource;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ba.slotbook.resource.ResourceImageService.ResourceImage;

// Slika ide kroz backend, a ne kao javni link na skladište, da i za nju važi provjera pristupa
@RestController
public class ResourceImageController {

	private final ResourceImageService imageService;

	public ResourceImageController(ResourceImageService imageService) {
		this.imageService = imageService;
	}

	@PostMapping(path = "/api/resources/{resourceId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@resourceAccess.canManage(#resourceId, authentication)")
	public ResourceResponse upload(@PathVariable UUID resourceId, @RequestParam("file") MultipartFile file)
			throws IOException {
		return imageService.upload(resourceId, file.getBytes());
	}

	@GetMapping("/api/resources/{resourceId}/image")
	@PreAuthorize("@resourceAccess.canView(#resourceId, authentication)")
	public ResponseEntity<byte[]> image(@PathVariable UUID resourceId) {
		ResourceImage slika = imageService.image(resourceId);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(slika.contentType())).body(slika.content());
	}

}
