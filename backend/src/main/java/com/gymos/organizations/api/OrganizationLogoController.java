package com.gymos.organizations.api;

import com.gymos.common.api.BusinessException;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.organizations.application.OrganizationLogoService;
import com.gymos.organizations.application.OrganizationLogoService.UploadOrganizationLogo;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.OrganizationSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationLogoController {
	private final OrganizationLogoService logoService;

	public OrganizationLogoController(OrganizationLogoService logoService) {
		this.logoService = logoService;
	}

	@PostMapping(value = "/current/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	OrganizationSummary upload(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestPart("file") MultipartFile file,
			HttpServletRequest request
	) {
		return logoService.upload(principal, uploadCommand(file), correlationId(request));
	}

	@PostMapping(value = "/{organizationId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	OrganizationSummary uploadManaged(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID organizationId,
			@RequestPart("file") MultipartFile file,
			HttpServletRequest request
	) {
		return logoService.uploadManaged(
				principal,
				organizationId,
				uploadCommand(file),
				correlationId(request)
		);
	}

	private UploadOrganizationLogo uploadCommand(MultipartFile file) {
		try {
			return new UploadOrganizationLogo(
					file.getOriginalFilename(),
					file.getContentType(),
					file.getSize(),
					file.getInputStream()
			);
		} catch (IOException exception) {
			throw new BusinessException("ORGANIZATION_LOGO_INVALID", "No fue posible leer la imagen.");
		}
	}

	@GetMapping("/{organizationId}/logo")
	ResponseEntity<byte[]> read(@PathVariable UUID organizationId) {
		return logoService.read(organizationId)
				.map(logo -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType(logo.contentType()))
						.header("Cache-Control", "public, max-age=31536000, immutable")
						.body(logo.content()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}
}
