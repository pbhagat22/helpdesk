package com.example.helpdesk.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.helpdesk.eval.EvalFiles;
import com.example.helpdesk.eval.EvalMeter;
import com.example.helpdesk.eval.EvalReport;
import com.example.helpdesk.eval.EvalSeed;
import com.example.helpdesk.eval.EvalTest;

// How well the agent answers staff questions (eval/agent-cases.json): AgentService.ask on the
// seeded data, as the first seeded agent, with the real tool-use loop and tools.
//
// Checks per run (each only where the case defines it):
// - requiredTools: the answer needs these tools (e.g. search_knowledge_base for policies)
// - tickets / notTickets: these tickets are / aren't named as #<id> in the answer
// - mentions / forbidden: as in AssistEvalTests
// - noGuessedIds: whenever list_tickets filters by assigneeId or reporterId, find_users ran
//   before it (the prompt's "never guess an id"); only scored on runs that filter by an id
// - noMarkdown: the UI shows plain text
// - efficient: at most maxModelCalls model calls (default 4). No floor: it's for comparing
//   prompts and models, since every call resends the whole conversation.
//
// Questions refer to tickets as {ticket:<title>}, replaced with the seeded id.
@EvalTest
class AgentEvalTests {

	record Cases(Map<String, Double> floors, List<Case> cases) {
	}

	record Case(String id, String question, List<String> requiredTools, List<String> tickets,
			List<String> notTickets, List<List<String>> mentions, List<String> forbidden, Integer maxModelCalls) {
	}

	private static final Pattern ID_FILTER = Pattern.compile("\"(assigneeId|reporterId)\"\\s*:\\s*\\d");

	private static final Pattern MARKDOWN = Pattern.compile("(?m)\\*\\*|^#{1,6} |^\\|.*\\|\\s*$");

	@Test
	void agent(@Autowired AgentService agentService, @Autowired EvalSeed seed, @Autowired EvalMeter meter) {
		Cases cases = EvalFiles.read("eval/agent-cases.json", Cases.class);
		EvalReport report = new EvalReport("agent", cases.floors(), meter);
		for (Case c : cases.cases()) {
			String question = seed.resolve(c.question());
			report.run(c.id(), run -> {
				AgentResponse response = agentService.ask(question, seed.agent());
				String answer = response.answer();
				String lower = answer.toLowerCase(Locale.ROOT);
				List<String> tools = response.steps().stream().map(AgentResponse.Step::tool).toList();
				String got = "%d calls, tools %s, answer: %s".formatted(response.modelCalls(), steps(response),
						shorten(answer));

				if (c.requiredTools() != null) {
					run.check("requiredTools", tools.containsAll(c.requiredTools()), got);
				}
				if (c.tickets() != null) {
					List<String> missing = c.tickets().stream().filter(t -> !names(answer, seed.ticketId(t))).toList();
					run.check("tickets", missing.isEmpty(), "missing " + missing + ", " + got);
				}
				if (c.notTickets() != null) {
					List<String> wrong = c.notTickets().stream().filter(t -> names(answer, seed.ticketId(t))).toList();
					run.check("notTickets", wrong.isEmpty(), "named " + wrong + ", " + got);
				}
				if (c.mentions() != null) {
					List<List<String>> missing = c.mentions()
						.stream()
						.filter(group -> group.stream().noneMatch(m -> lower.contains(m.toLowerCase(Locale.ROOT))))
						.toList();
					run.check("mentions", missing.isEmpty(), "missing " + missing + ", " + got);
				}
				if (c.forbidden() != null) {
					List<String> found = c.forbidden()
						.stream()
						.filter(f -> lower.contains(f.toLowerCase(Locale.ROOT)))
						.toList();
					run.check("forbidden", found.isEmpty(), "contains " + found + ", " + got);
				}
				guessedIds(response.steps()).ifPresent(ok -> run.check("noGuessedIds", ok, got));
				run.check("noMarkdown", !MARKDOWN.matcher(answer).find(), got);
				int maxCalls = c.maxModelCalls() != null ? c.maxModelCalls() : 4;
				run.check("efficient", response.modelCalls() <= maxCalls, got);
			});
		}
		report.finish();
	}

	// "#12" but not "#123"
	private static boolean names(String answer, Long ticketId) {
		return Pattern.compile("#" + ticketId + "(?!\\d)").matcher(answer).find();
	}

	// Empty if no list_tickets call filtered by an id; otherwise whether find_users came first
	private static Optional<Boolean> guessedIds(List<AgentResponse.Step> steps) {
		boolean applies = false;
		for (int i = 0; i < steps.size(); i++) {
			AgentResponse.Step step = steps.get(i);
			if (step.tool().equals("list_tickets") && ID_FILTER.matcher(step.input()).find()) {
				applies = true;
				boolean lookedUp = steps.subList(0, i).stream().anyMatch(s -> s.tool().equals("find_users"));
				if (!lookedUp) {
					return Optional.of(false);
				}
			}
		}
		return applies ? Optional.of(true) : Optional.empty();
	}

	private static String steps(AgentResponse response) {
		return response.steps()
			.stream()
			.map(s -> s.tool() + s.input() + (s.error() != null ? "!" : ""))
			.toList()
			.toString();
	}

	private static String shorten(String text) {
		String oneLine = text.replaceAll("\\s+", " ");
		return oneLine.length() <= 300 ? oneLine : oneLine.substring(0, 300) + "…";
	}

}
