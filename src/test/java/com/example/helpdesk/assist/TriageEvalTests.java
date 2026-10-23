package com.example.helpdesk.assist;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.helpdesk.eval.EvalFiles;
import com.example.helpdesk.eval.EvalMeter;
import com.example.helpdesk.eval.EvalReport;
import com.example.helpdesk.eval.EvalTest;
import com.example.helpdesk.ticket.Category;
import com.example.helpdesk.ticket.Priority;
import com.example.helpdesk.ticket.TicketResponse;
import com.example.helpdesk.ticket.TicketStatus;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.UserResponse;

// How well the triage model picks priority and category (eval/triage-cases.json), through
// TicketTriageService.suggest: the production prompt and schema, without saving anything.
//
// The tickets are built in memory, not saved: they only go into the prompt, and saved ones
// would show up in the agent eval's ticket lists.
//
// Checks per run: category is one of the acceptable ones; priority is one of the acceptable
// ones; priority is at most one level away from an acceptable one (MEDIUM for HIGH is a small
// miss, LOW for URGENT a serious one).
@EvalTest
class TriageEvalTests {

	record Cases(Map<String, Double> floors, List<Case> cases) {
	}

	// priority and category list the acceptable answers; the first is the ideal one
	record Case(String id, String title, String description, Priority reporterPriority, List<Priority> priority,
			List<Category> category) {
	}

	private static final UserResponse REPORTER = new UserResponse(0L, "customer@example.com", "Eval Customer",
			Role.CUSTOMER, Instant.EPOCH);

	@Test
	void triage(@Autowired TicketTriageService triageService, @Autowired EvalMeter meter) {
		Cases cases = EvalFiles.read("eval/triage-cases.json", Cases.class);
		EvalReport report = new EvalReport("triage", cases.floors(), meter);
		long id = 0;
		for (Case c : cases.cases()) {
			TicketResponse ticket = new TicketResponse(++id, c.title(), c.description(), TicketStatus.OPEN,
					c.reporterPriority(), null, REPORTER, null, Instant.now(), Instant.now());
			report.run(c.id(), run -> {
				TriageAnswer answer = triageService.suggest(ticket);
				// The model's reason helps to judge whether the case or the model is wrong
				String got = "got %s/%s, expected %s/%s (reason: %s)".formatted(answer.priority(), answer.category(),
						c.priority(), c.category(), answer.reason());
				run.check("category", c.category().contains(answer.category()), got);
				run.check("priority", c.priority().contains(answer.priority()), got);
				run.check("priorityWithinOne", distance(answer.priority(), c.priority()) <= 1, got);
			});
		}
		report.finish();
	}

	// Levels between the answer and the nearest acceptable priority (enum order LOW..URGENT)
	private static int distance(Priority answer, List<Priority> acceptable) {
		return acceptable.stream().mapToInt(p -> Math.abs(p.ordinal() - answer.ordinal())).min().orElseThrow();
	}

}
