package com.example.helpdesk.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DocumentsTests {

	@Test
	void roundTripsJsonValues() {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("text", "hi");
		value.put("count", 5L);
		value.put("ratio", new BigDecimal("0.5"));
		value.put("flag", true);
		value.put("nothing", null);
		value.put("list", Arrays.asList(1L, null, "x"));
		value.put("nested", Map.of("a", List.of()));

		assertThat(Documents.toJava(Documents.fromJava(value))).isEqualTo(value);
	}

	@Test
	void wholeNumbersBecomeLongs() {
		assertThat(Documents.toJava(Documents.fromJava(7))).isEqualTo(7L);
		assertThat(Documents.toJava(Documents.fromJava(new BigDecimal("7.0")))).isEqualTo(7L);
	}

}
