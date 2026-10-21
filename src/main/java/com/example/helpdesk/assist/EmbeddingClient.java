package com.example.helpdesk.assist;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.helpdesk.common.ServiceUnavailableException;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Turns text into an embedding (a vector of numbers capturing its meaning) with Amazon Titan Text
// Embeddings V2 on Bedrock.
//
// Embedding models aren't chat models, so this doesn't use Converse: InvokeModel sends the
// model's own JSON format. Titan V2: {"inputText", "dimensions", "normalize"} in,
// {"embedding": [...], "inputTextTokenCount": n} out.
@Component
public class EmbeddingClient {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

	// Must match the vector(512) column in V5. Titan V2 offers 256, 512 or 1024: more dimensions
	// capture a bit more nuance, but cost more storage and slower searches.
	public static final int DIMENSIONS = 512;

	private final BedrockRuntimeClient bedrock;

	private final AiProperties properties;

	private final JsonMapper jsonMapper;

	public EmbeddingClient(BedrockRuntimeClient bedrock, AiProperties properties, JsonMapper jsonMapper) {
		this.bedrock = bedrock;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
	}

	record TitanResponse(List<Float> embedding, int inputTextTokenCount) {
	}

	// normalize: every vector gets length 1, so cosine distance (pgvector's <=>) only compares
	// directions, i.e. meaning, not how long the text was.
	public float[] embed(String text) {
		String body = jsonMapper
			.writeValueAsString(Map.of("inputText", text, "dimensions", DIMENSIONS, "normalize", true));
		InvokeModelResponse response;
		try {
			response = bedrock.invokeModel(InvokeModelRequest.builder()
				.modelId(properties.embeddingModel())
				.contentType("application/json")
				.accept("application/json")
				.body(SdkBytes.fromUtf8String(body))
				.build());
		}
		catch (SdkException ex) {
			throw new ServiceUnavailableException("The embedding model is unavailable right now", ex);
		}
		TitanResponse titan;
		try {
			titan = jsonMapper.readValue(response.body().asUtf8String(), TitanResponse.class);
		}
		catch (JacksonException ex) {
			throw new ServiceUnavailableException("The embedding model returned an unexpected response", ex);
		}
		if (titan.embedding() == null || titan.embedding().size() != DIMENSIONS) {
			throw new ServiceUnavailableException("The embedding model returned a vector of the wrong size", null);
		}
		log.debug("Embedded {} tokens", titan.inputTextTokenCount());
		float[] vector = new float[DIMENSIONS];
		for (int i = 0; i < DIMENSIONS; i++) {
			vector[i] = titan.embedding().get(i);
		}
		return vector;
	}

}
