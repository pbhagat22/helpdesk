package com.example.helpdesk.agent;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.core.SdkNumber;
import software.amazon.awssdk.core.document.Document;

// The AWS SDK represents free-form JSON (tool input schemas, the model's tool inputs) as
// Document. These convert between Document and plain Java values (Map, List, String, Number,
// Boolean, null), which Jackson can read and write.
final class Documents {

	private Documents() {
	}

	static Document fromJava(Object value) {
		return switch (value) {
			case null -> Document.fromNull();
			case Map<?, ?> map -> {
				Map<String, Document> members = new LinkedHashMap<>();
				map.forEach((key, member) -> members.put(String.valueOf(key), fromJava(member)));
				yield Document.fromMap(members);
			}
			case List<?> list -> Document.fromList(list.stream().map(Documents::fromJava).toList());
			case String text -> Document.fromString(text);
			case Boolean bool -> Document.fromBoolean(bool);
			case Number number -> Document.fromNumber(SdkNumber.fromString(number.toString()));
			default -> throw new IllegalArgumentException("Not a JSON value: " + value.getClass());
		};
	}

	static Object toJava(Document document) {
		if (document == null || document.isNull()) {
			return null;
		}
		if (document.isMap()) {
			Map<String, Object> map = new LinkedHashMap<>();
			document.asMap().forEach((key, member) -> map.put(key, toJava(member)));
			return map;
		}
		if (document.isList()) {
			// Not toList(): a JSON array may contain nulls
			return document.asList().stream().map(Documents::toJava).collect(java.util.stream.Collectors.toList());
		}
		if (document.isString()) {
			return document.asString();
		}
		if (document.isBoolean()) {
			return document.asBoolean();
		}
		BigDecimal number = document.asNumber().bigDecimalValue();
		// Whole numbers as Long, so "5" fits a Long field such as a ticket id
		return number.stripTrailingZeros().scale() <= 0 ? (Object) number.longValueExact() : number;
	}

}
