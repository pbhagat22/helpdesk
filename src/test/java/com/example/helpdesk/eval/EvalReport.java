package com.example.helpdesk.eval;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.example.helpdesk.eval.EvalMeter.Usage;

// Scores one feature's eval. A model's answers vary between runs, so every case runs RUNS
// times, and each check's score is the share of runs that passed it (e.g. category 34/36).
//
//   EvalReport report = new EvalReport("triage", cases.floors(), meter);
//   for (Case c : cases.cases()) {
//       report.run(c.id(), run -> run.check("category", answer.category() == c.category(), "got ..."));
//   }
//   report.finish();
//
// "answered" is recorded for every run: false if the call threw (e.g. a 503 for an invalid
// answer). The other checks only count runs that answered, so reliability and quality are
// scored separately.
//
// finish() prints a table, writes target/eval-reports/<feature>-<time>.json (for comparing
// runs, e.g. two models) and fails if any score is below its floor. Floors sit a bit below what
// the current model usually scores, so normal run-to-run noise passes and a real drop doesn't.
public class EvalReport {

	// -Deval.runs=1 for a quick, cheap check while writing cases
	public static final int RUNS = Integer.getInteger("eval.runs", 3);

	public static final String ANSWERED = "answered";

	private final String feature;

	private final Map<String, Double> floors;

	private final EvalMeter meter;

	private final String startedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

	// Insertion order everywhere, so the table follows the case file
	private final Map<String, CaseResult> cases = new LinkedHashMap<>();

	private final Set<String> checkNames = new LinkedHashSet<>(List.of(ANSWERED));

	public EvalReport(String feature, Map<String, Double> floors, EvalMeter meter) {
		this.feature = feature;
		this.floors = floors;
		this.meter = meter;
	}

	// Runs one case RUNS times
	public void run(String caseId, Consumer<Run> body) {
		CaseResult result = cases.computeIfAbsent(caseId, id -> new CaseResult());
		for (int i = 1; i <= RUNS; i++) {
			long mark = meter.mark();
			long start = System.nanoTime();
			Run run = new Run(i, result);
			try {
				body.accept(run);
				run.check(ANSWERED, true, null);
			}
			catch (RuntimeException ex) {
				run.check(ANSWERED, false, ex.getClass().getSimpleName() + ": " + ex.getMessage());
			}
			run.failed.forEach((detail, names) -> result.failures
				.add("run " + run.number + ": " + String.join(", ", names) + (detail.isEmpty() ? "" : ": " + detail)));
			result.millis += (System.nanoTime() - start) / 1_000_000;
			result.runs++;
			result.usage = result.usage.plus(meter.since(mark));
		}
	}

	public final class Run {

		private final int number;

		private final CaseResult result;

		// Failed checks by detail, so checks that failed for the same reason share one line
		private final Map<String, List<String>> failed = new LinkedHashMap<>();

		private Run(int number, CaseResult result) {
			this.number = number;
			this.result = result;
		}

		// detail says what went wrong; it's only shown when the check failed
		public void check(String name, boolean passed, String detail) {
			checkNames.add(name);
			Score score = result.scores.computeIfAbsent(name, n -> new Score());
			score.total++;
			if (passed) {
				score.passed++;
			}
			else {
				failed.computeIfAbsent(detail != null ? detail : "", d -> new ArrayList<>()).add(name);
			}
		}

	}

	// Prints and saves the report, then fails if a score is below its floor
	public void finish() {
		Map<String, Score> totals = totals();
		Usage usage = cases.values().stream().map(c -> c.usage).reduce(Usage.ZERO, Usage::plus);
		print(totals, usage);
		Path file = Path.of("target", "eval-reports", feature + "-" + startedAt + ".json");
		EvalFiles.write(file, new ReportFile(feature, startedAt, RUNS, meter.models(), floors, totals, usage,
				cases.entrySet().stream().map(e -> e.getValue().toFile(e.getKey())).toList()));
		System.out.println("Report: " + file.toAbsolutePath());
		assertFloors(totals);
	}

	private Map<String, Score> totals() {
		Map<String, Score> totals = new LinkedHashMap<>();
		for (String name : checkNames) {
			Score total = new Score();
			for (CaseResult result : cases.values()) {
				Score score = result.scores.get(name);
				if (score != null) {
					total.passed += score.passed;
					total.total += score.total;
				}
			}
			totals.put(name, total);
		}
		return totals;
	}

	private void print(Map<String, Score> totals, Usage usage) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n=== %s eval: %d cases x %d runs, %s ===\n".formatted(feature, cases.size(), RUNS, meter.models()));
		int idWidth = Math.max(4, cases.keySet().stream().mapToInt(String::length).max().orElse(4));
		sb.append(("%-" + idWidth + "s").formatted("case"));
		checkNames.forEach(name -> sb.append("  %-10s".formatted(shorten(name))));
		sb.append("  avg s/run\n");
		cases.forEach((id, result) -> {
			sb.append(("%-" + idWidth + "s").formatted(id));
			for (String name : checkNames) {
				Score score = result.scores.get(name);
				sb.append("  %-10s".formatted(score == null ? "-" : score.passed + "/" + score.total));
			}
			sb.append("  %.1f%n".formatted(result.millis / 1000.0 / result.runs));
		});
		sb.append("\nscore by check:\n");
		totals.forEach((name, score) -> {
			Double floor = floors.get(name);
			String verdict = floor == null ? "(no floor)" : score.rate() >= floor ? "ok" : "BELOW FLOOR";
			sb.append("  %-22s %3.0f%%  (%d/%d)  floor %s  %s%n".formatted(name, score.rate() * 100, score.passed,
					score.total, floor == null ? "-" : "%.0f%%".formatted(floor * 100), verdict));
		});
		sb.append("\ntokens: %d in, %d out, estimated cost $%s%n".formatted(usage.inputTokens(),
				usage.outputTokens(), usage.costUsd().toPlainString()));
		List<String> failures = new ArrayList<>();
		cases.forEach((id, result) -> result.failures.forEach(f -> failures.add(id + " " + f)));
		if (!failures.isEmpty()) {
			sb.append("\nfailed checks:\n");
			failures.forEach(f -> sb.append("  ").append(f).append('\n'));
		}
		System.out.println(sb);
	}

	private void assertFloors(Map<String, Score> totals) {
		List<String> problems = new ArrayList<>();
		floors.forEach((name, floor) -> {
			Score score = totals.get(name);
			if (score == null || score.total == 0) {
				// A floor for a check that never ran is a typo in the case file, or a broken eval
				problems.add(name + ": no results");
			}
			else if (score.rate() < floor) {
				problems.add("%s: %.0f%% < floor %.0f%%".formatted(name, score.rate() * 100, floor * 100));
			}
		});
		if (!problems.isEmpty()) {
			throw new AssertionError(feature + " eval below floor: " + String.join("; ", problems));
		}
	}

	private static String shorten(String name) {
		return name.length() <= 10 ? name : name.substring(0, 10);
	}

	static final class Score {

		int passed;

		int total;

		double rate() {
			return total == 0 ? 0 : (double) passed / total;
		}

		public int getPassed() {
			return passed;
		}

		public int getTotal() {
			return total;
		}

	}

	private static final class CaseResult {

		final Map<String, Score> scores = new LinkedHashMap<>();

		final List<String> failures = new ArrayList<>();

		int runs;

		long millis;

		Usage usage = Usage.ZERO;

		CaseFile toFile(String id) {
			return new CaseFile(id, runs, scores, failures, millis / runs, usage);
		}

	}

	// The JSON report
	record ReportFile(String feature, String startedAt, int runsPerCase, Map<String, String> models,
			Map<String, Double> floors, Map<String, Score> scores, Usage usage, List<CaseFile> cases) {
	}

	record CaseFile(String id, int runs, Map<String, Score> scores, List<String> failures, long avgMillis,
			Usage usage) {
	}

}
