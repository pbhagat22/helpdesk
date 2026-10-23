package com.example.helpdesk.assist;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

// helpdesk.ai.pricing.models[<model id>].input-per-million / output-per-million: Bedrock's
// on-demand price in USD per million tokens, used for the usage report's cost estimate.
// Prices are configuration because they change; a model without a price is reported as
// unpriced rather than as free.
@ConfigurationProperties("helpdesk.ai.pricing")
public record AiPricing(Map<String, ModelPrice> models) {

	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

	public record ModelPrice(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
	}

	public AiPricing {
		models = models == null ? Map.of() : Map.copyOf(models);
	}

	public boolean knows(String model) {
		return models.containsKey(model);
	}

	// Estimated USD for the tokens, 0 if the model has no price
	public BigDecimal cost(String model, long inputTokens, long outputTokens) {
		ModelPrice price = models.get(model);
		if (price == null) {
			return BigDecimal.ZERO;
		}
		return price.inputPerMillion()
			.multiply(BigDecimal.valueOf(inputTokens))
			.add(price.outputPerMillion().multiply(BigDecimal.valueOf(outputTokens)))
			.divide(MILLION, 6, RoundingMode.HALF_UP);
	}

}
