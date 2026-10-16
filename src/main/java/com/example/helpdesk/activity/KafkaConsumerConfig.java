package com.example.helpdesk.activity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConsumerConfig {

	// When a listener throws (e.g. the database is briefly unavailable), retry the same message
	// twice, one second apart. If it still fails, log it and move on to the next message, so one
	// bad message can't block the partition forever. Spring Boot applies this bean to every
	// @KafkaListener. (Production systems often send such messages to a "dead letter" topic.)
	@Bean
	CommonErrorHandler kafkaErrorHandler() {
		return new DefaultErrorHandler(new FixedBackOff(1000L, 2));
	}

}
