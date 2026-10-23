package com.example.helpdesk.assist;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.helpdesk.eval.EvalFiles;
import com.example.helpdesk.eval.EvalMeter;
import com.example.helpdesk.eval.EvalReport;
import com.example.helpdesk.eval.EvalSeed;
import com.example.helpdesk.eval.EvalTest;

// How good the assist answers are (eval/assist-cases.json): TicketAssistService.suggest on the
// seeded tickets, as the first seeded agent, so the knowledge base search (RAG) is part of it.
//
// Checks per run:
// - sources: every expected article is in the answer's sources (retrieved AND used)
// - noExtraSources: no other article is cited (none at all where nothing fits)
// - mentions: each group of alternatives appears in the reply (case-insensitive), e.g. the
//   refund time from the article
// - forbidden: none of the case's forbidden phrases appear (e.g. an injected promise)
// - noPlaceholders, noSignOff: the system prompt's rules, on every case
//
// Plain string checks can't judge tone or helpfulness; that would need a second model as a
// judge, which is left for later.
@EvalTest
class AssistEvalTests {

	record Cases(Map<String, Double> floors, List<Case> cases) {
	}

	// ticket: a title from eval/seed.json
	record Case(String id, String ticket, List<String> sources, List<List<String>> mentions, List<String> forbidden) {
	}

	// [Name], [Your Company], [date] ...
	private static final Pattern PLACEHOLDER = Pattern.compile("\\[[A-Za-z][A-Za-z ]{1,30}]");

	private static final Pattern SIGN_OFF = Pattern
		.compile("(?i)\\b(best regards|kind regards|warm regards|regards,|sincerely|cheers,)");

	@Test
	void assist(@Autowired TicketAssistService assistService, @Autowired EvalSeed seed,
			@Autowired EvalMeter meter) {
		Cases cases = EvalFiles.read("eval/assist-cases.json", Cases.class);
		EvalReport report = new EvalReport("assist", cases.floors(), meter);
		for (Case c : cases.cases()) {
			Long ticketId = seed.ticketId(c.ticket());
			report.run(c.id(), run -> {
				AssistResponse answer = assistService.suggest(ticketId, seed.agent());
				String reply = answer.suggestedReply();
				String lower = reply.toLowerCase(Locale.ROOT);
				List<String> cited = answer.sources().stream().map(AssistResponse.Source::title).toList();
				String got = "sources " + cited + ", reply: " + shorten(reply);

				if (!c.sources().isEmpty()) {
					run.check("sources", cited.containsAll(c.sources()), got);
				}
				run.check("noExtraSources", c.sources().containsAll(cited), got);
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
				run.check("noPlaceholders", !PLACEHOLDER.matcher(reply).find(), got);
				run.check("noSignOff", !SIGN_OFF.matcher(reply).find(), got);
			});
		}
		report.finish();
	}

	private static String shorten(String text) {
		String oneLine = text.replaceAll("\\s+", " ");
		return oneLine.length() <= 300 ? oneLine : oneLine.substring(0, 300) + "…";
	}

}
