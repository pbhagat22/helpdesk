package com.example.helpdesk.assist;

import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.user.Role;

// Who a Bedrock call is for, passed explicitly with every call (like the user everywhere else),
// so the usage ledger and the budgets know which feature and user to count it against, and
// which role's budget applies. userId and role are null for background work.
public record AiCaller(AiFeature feature, Long userId, Role role) {

	public static AiCaller user(AiFeature feature, HelpdeskUserDetails me) {
		return new AiCaller(feature, me.getId(), me.getRole());
	}

	public static AiCaller background(AiFeature feature) {
		return new AiCaller(feature, null, null);
	}

}
