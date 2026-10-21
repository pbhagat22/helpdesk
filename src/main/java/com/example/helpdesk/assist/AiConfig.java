package com.example.helpdesk.assist;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration(proxyBeanMethods = false)
public class AiConfig {

	// The AWS SDK client for Bedrock's runtime API. Requests are signed with AWS credentials
	// (SigV4), so there's no API key: the default chain finds them in AWS_PROFILE / ~/.aws
	// locally, and the ECS task role on AWS.
	// Credentials are only looked up on the first call, so the app (and the tests) start
	// fine without any; the AI endpoint then answers 503.
	@Bean(destroyMethod = "close")
	BedrockRuntimeClient bedrockRuntimeClient(AiProperties properties) {
		return BedrockRuntimeClient.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(DefaultCredentialsProvider.builder().build())
			// A user is waiting on this request, so don't let it hang (retries included)
			.overrideConfiguration(config -> config.apiCallTimeout(properties.timeout()))
			.build();
	}

}
