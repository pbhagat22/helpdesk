package com.example.helpdesk.assist;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// What the AI features cost: per day, feature and model, and the users who spend the most.
@RestController
public class AiUsageController {

	private final AiUsageService usage;

	public AiUsageController(AiUsageService usage) {
		this.usage = usage;
	}

	// GET /api/admin/ai/usage?days=7 : the last 7 UTC days, today included
	@GetMapping("/api/admin/ai/usage")
	@PreAuthorize("hasRole('ADMIN')")
	public AiUsageReport report(@RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
		return usage.report(days);
	}

}
