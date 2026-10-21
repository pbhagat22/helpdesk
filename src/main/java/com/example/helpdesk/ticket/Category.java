package com.example.helpdesk.ticket;

// What a ticket is about. Customers don't pick it; staff set it, helped by the AI triage
// suggestion. A ticket without one (null) is "uncategorised".
public enum Category {
	ACCOUNT,
	BILLING,
	BUG,
	HOW_TO,
	OTHER
}
