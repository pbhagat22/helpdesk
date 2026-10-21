package com.example.helpdesk.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.helpdesk.common.ServiceUnavailableException;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import tools.jackson.databind.json.JsonMapper;

class EmbeddingClientTests {

	private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);

	private final EmbeddingClient client = new EmbeddingClient(bedrock,
			new AiProperties("us-east-2", "m", "a", "titan-test", Duration.ofSeconds(5)), JsonMapper.builder().build());

	private static InvokeModelResponse titanResponse(int dimensions) {
		String vector = String.join(",", Collections.nCopies(dimensions, "0.5"));
		return InvokeModelResponse.builder()
			.body(SdkBytes.fromUtf8String("{\"embedding\": [" + vector + "], \"inputTextTokenCount\": 3}"))
			.build();
	}

	@Test
	void sendsTitansRequestFormatAndReadsTheVector() {
		given(bedrock.invokeModel(any(InvokeModelRequest.class))).willReturn(titanResponse(EmbeddingClient.DIMENSIONS));

		float[] vector = client.embed("printer on fire");

		assertThat(vector).hasSize(512).containsOnly(0.5f);
		ArgumentCaptor<InvokeModelRequest> request = ArgumentCaptor.forClass(InvokeModelRequest.class);
		verify(bedrock).invokeModel(request.capture());
		assertThat(request.getValue().modelId()).isEqualTo("titan-test");
		assertThat(request.getValue().body().asUtf8String()).contains("\"inputText\":\"printer on fire\"")
			.contains("\"dimensions\":512")
			.contains("\"normalize\":true");
	}

	@Test
	void wrongSizeIsServiceUnavailable() {
		given(bedrock.invokeModel(any(InvokeModelRequest.class))).willReturn(titanResponse(256));

		assertThatThrownBy(() -> client.embed("x")).isInstanceOf(ServiceUnavailableException.class);
	}

	@Test
	void bedrockErrorIsServiceUnavailable() {
		given(bedrock.invokeModel(any(InvokeModelRequest.class)))
			.willThrow(ThrottlingException.builder().message("Too many requests").build());

		assertThatThrownBy(() -> client.embed("x")).isInstanceOf(ServiceUnavailableException.class);
	}

}
