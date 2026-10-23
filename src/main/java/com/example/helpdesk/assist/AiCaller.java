package com.example.helpdesk.assist;

// Who a Bedrock call is for, passed explicitly with every call (like the user everywhere else),
// so the usage ledger and the budgets know which feature and user to count it against.
// userId is null for background work.
public record AiCaller(AiFeature feature, Long userId) {

	public static AiCaller user(AiFeature feature, Long userId) {
		return new AiCaller(feature, userId);
	}

	public static AiCaller background(AiFeature feature) {
		return new AiCaller(feature, null);
	}

}
