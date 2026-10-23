package com.example.helpdesk.eval;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.example.helpdesk.ElasticsearchTestcontainersConfiguration;
import com.example.helpdesk.KafkaTestcontainersConfiguration;
import com.example.helpdesk.TestcontainersConfiguration;

// Marks an LLM eval: the whole application against REAL Bedrock (no FakeBedrockConfiguration),
// with throwaway Postgres, Kafka and Elasticsearch, seeded once by EvalSeed. Run with
// AWS_PROFILE=helpdesk ./mvnw test -Peval. The "eval" tag keeps them out of a normal
// ./mvnw test and out of CI (see the Surefire configuration in pom.xml).
//
// A composed annotation, so every eval class has exactly the same configuration and Spring's
// test context cache gives them one shared context: one set of containers, seeded once.
//
// - Kafka listeners don't start: otherwise every seeded ticket would be triaged and indexed in
//   the background, costing money and changing the data while the evals run. EvalSeed builds
//   the search index directly, and the triage eval calls the model itself.
// - The MCP client is off, so nothing goes to AWS's documentation server: the evals measure
//   our own prompts and tools.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("eval")
@SpringBootTest(properties = { "spring.kafka.listener.auto-startup=false", "spring.ai.mcp.client.enabled=false" })
@Import({ TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class,
		ElasticsearchTestcontainersConfiguration.class, EvalSeed.class, EvalMeter.class })
public @interface EvalTest {

}
