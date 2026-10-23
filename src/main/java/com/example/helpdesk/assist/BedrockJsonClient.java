package com.example.helpdesk.assist;

import java.lang.reflect.RecordComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.helpdesk.common.ServiceUnavailableException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.JsonSchemaDefinition;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatStructure;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatType;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// One question to an LLM on Amazon Bedrock, answered as JSON that matches a schema, parsed
// into a record. The AI features only write prompts and schemas; this class does the rest.
//
// It uses Bedrock's Converse API, which has one request/response shape for every model
// (Qwen, Mistral, OpenAI's gpt-oss, Claude...), so the model is just configuration.
@Component
public class BedrockJsonClient {

	private static final Logger log = LoggerFactory.getLogger(BedrockJsonClient.class);

	// Upper bound on output tokens; the answers here are a few hundred at most
	private static final int MAX_TOKENS = 2000;

	private final BedrockRuntimeClient bedrock;

	private final AiProperties properties;

	private final JsonMapper jsonMapper;

	private final AiUsageService usage;

	private final AiBudget budget;

	public BedrockJsonClient(BedrockRuntimeClient bedrock, AiProperties properties, JsonMapper jsonMapper,
			AiUsageService usage, AiBudget budget) {
		this.bedrock = bedrock;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		this.usage = usage;
		this.budget = budget;
	}

	// caller: who the tokens are counted against. task: a short label for the logs, e.g.
	// "assist ticket 4". Throws TooManyRequestsException if a daily budget is used up, and
	// ServiceUnavailableException if there's no complete, valid answer.
	public <T extends Record> T ask(AiCaller caller, String task, String systemPrompt, String userPrompt,
			String schemaName, String schema, Class<T> answerType) {
		budget.check(caller);
		ConverseRequest request = ConverseRequest.builder()
			.modelId(properties.model())
			.system(SystemContentBlock.fromText(systemPrompt))
			.messages(Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText(userPrompt)).build())
			.inferenceConfig(config -> config.maxTokens(MAX_TOKENS))
			// "Structured output": Bedrock makes the answer match the JSON schema, so it can be
			// parsed straight into the record. Not every model supports it (some reject the
			// field, some accept it and ignore it), which is why the answer is still checked.
			.outputConfig(output -> output.textFormat(format -> format.type(OutputFormatType.JSON_SCHEMA)
				.structure(OutputFormatStructure
					.fromJsonSchema(JsonSchemaDefinition.builder().name(schemaName).schema(schema).build()))))
			.build();

		ConverseResponse response;
		try {
			response = bedrock.converse(request);
		}
		catch (SdkException ex) {
			// Bedrock answered with an error (no model access, throttling...), the call timed
			// out, or AWS credentials are missing or expired.
			throw new ServiceUnavailableException("The AI assistant is unavailable right now", ex);
		}

		// Tokens are what Bedrock bills for, so log and record them for every call.
		log.info("AI {}: model={}, stop={}, input tokens={}, output tokens={}", task, properties.model(),
				response.stopReason(), response.usage().inputTokens(), response.usage().outputTokens());
		usage.record(caller, properties.model(), response.usage().inputTokens(), response.usage().outputTokens());

		// Anything but a normal end (e.g. max_tokens: cut off mid-answer) means there's no complete answer.
		if (response.stopReason() != StopReason.END_TURN) {
			throw new ServiceUnavailableException("The AI assistant couldn't finish its answer", null);
		}
		// Some models also return a "reasoning" block; the answer is the text block.
		String json = response.output()
			.message()
			.content()
			.stream()
			.map(ContentBlock::text)
			.filter(text -> text != null && !text.isBlank())
			.findFirst()
			.orElseThrow(() -> new ServiceUnavailableException("The AI assistant returned an empty answer", null));
		T answer;
		try {
			answer = jsonMapper.readValue(json, answerType);
		}
		catch (JacksonException ex) {
			// Not JSON, or a value the record can't hold (e.g. a priority that doesn't exist)
			throw new ServiceUnavailableException("The AI assistant returned an answer that isn't valid JSON", ex);
		}
		// Every field is required by the schema, but check rather than trust the model.
		for (RecordComponent component : answerType.getRecordComponents()) {
			Object value;
			try {
				value = component.getAccessor().invoke(answer);
			}
			catch (ReflectiveOperationException ex) {
				throw new IllegalStateException(ex);
			}
			if (value == null) {
				throw new ServiceUnavailableException(
						"The AI assistant's answer is missing \"" + component.getName() + "\"", null);
			}
		}
		return answer;
	}

}
