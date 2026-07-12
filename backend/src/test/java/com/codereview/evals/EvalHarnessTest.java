package com.codereview.evals;

import com.codereview.domain.IssueCategory;
import com.codereview.service.ai.AiReviewResult;
import com.codereview.service.ai.AiReviewServiceImpl;
import com.codereview.service.ai.FindingAnchorValidator;
import com.codereview.service.ai.MockReviewEngine;
import com.codereview.service.ai.ReviewJsonParser;
import com.codereview.service.ai.ReviewPromptFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evaluation harness: runs the review engine(s) against a labeled dataset of diffs with
 * planted bugs ({@code src/test/resources/evals/cases.json}) and measures precision and
 * recall per category, split by whether the bug is detectable by simple heuristics
 * ({@code HEURISTIC}) or requires semantic understanding ({@code SEMANTIC}). Clean diffs
 * measure false positives.
 *
 * <p>The deterministic heuristic engine always runs and has asserted floors (this is the
 * regression gate). When {@code ANTHROPIC_API_KEY} is present in the environment, the real
 * Claude engine is also evaluated and included in the report, without hard assertions
 * (LLM output varies run to run).
 *
 * <p>Matching rule: a finding counts as a true positive when it lands in the expected file,
 * its category is one of the accepted categories for that bug, and its line is within
 * {@value #LINE_TOLERANCE} of the labeled line (file-level findings match any line).
 * {@code TEST_COVERAGE} findings are excluded from scoring entirely — every no-test diff
 * in the dataset would trivially trigger them.
 *
 * <p>The report is written to {@code target/evals/report.json}; a committed copy at
 * {@code src/main/resources/evals/report.json} backs the public {@code /api/evals/report}
 * endpoint and the dashboard's Evals page.
 */
class EvalHarnessTest {

    private static final int LINE_TOLERANCE = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record ExpectedBug(String filePath, Integer lineNumber, Set<String> categories) {
    }

    record EvalCase(String id, String title, String detectableBy, String diff,
                    List<ExpectedBug> expected) {
    }

    record EngineScore(String engine, int tp, int fn, int fp,
                       int heuristicTp, int heuristicTotal,
                       int semanticTp, int semanticTotal,
                       int cleanFalsePositives, int demotedAnchors,
                       Map<IssueCategory, int[]> perCategory) {

        double precision() {
            return tp + fp == 0 ? 1.0 : (double) tp / (tp + fp);
        }

        double recall() {
            return tp + fn == 0 ? 1.0 : (double) tp / (tp + fn);
        }

        double f1() {
            double p = precision(), r = recall();
            return p + r == 0 ? 0 : 2 * p * r / (p + r);
        }
    }

    private static List<EvalCase> cases;

    @BeforeAll
    static void loadCases() throws Exception {
        try (InputStream in = EvalHarnessTest.class.getResourceAsStream("/evals/cases.json")) {
            JsonNode root = MAPPER.readTree(in);
            List<EvalCase> loaded = new ArrayList<>();
            for (JsonNode c : root) {
                List<ExpectedBug> expected = new ArrayList<>();
                for (JsonNode e : c.path("expected")) {
                    // LinkedHashSet: the first listed category is the bug's primary
                    // category for per-category reporting.
                    Set<String> categories = new LinkedHashSet<>();
                    e.path("categories").forEach(n -> categories.add(n.asText()));
                    expected.add(new ExpectedBug(e.path("filePath").asText(),
                            e.hasNonNull("lineNumber") ? e.get("lineNumber").asInt() : null,
                            categories));
                }
                loaded.add(new EvalCase(c.path("id").asText(), c.path("title").asText(),
                        c.path("detectableBy").asText(), c.path("diff").asText(), expected));
            }
            cases = loaded;
        }
    }

    @Test
    @DisplayName("dataset is well-formed: every labeled line exists in its diff")
    void datasetAnchorsAreValid() {
        FindingAnchorValidator validator = new FindingAnchorValidator();
        for (EvalCase c : cases) {
            for (ExpectedBug bug : c.expected()) {
                var probe = new AiReviewResult.Finding(bug.filePath(), bug.lineNumber(),
                        IssueCategory.CODE_QUALITY, com.codereview.domain.Severity.INFO, "", "");
                var result = validator.validate(c.diff(), List.of(probe));
                assertThat(result.demoted())
                        .as("case %s: labeled line %s must exist in the diff", c.id(), bug.lineNumber())
                        .isZero();
                assertThat(result.snapped())
                        .as("case %s: labeled line %s must be exact", c.id(), bug.lineNumber())
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("heuristic engine meets its floors; report written")
    void runEvals() throws Exception {
        MockReviewEngine mock = new MockReviewEngine();
        List<EngineScore> scores = new ArrayList<>();

        EngineScore heuristic = score("heuristic", (title, diff) -> mock.review(title, diff));
        scores.add(heuristic);

        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            AiReviewServiceImpl claude = new AiReviewServiceImpl(new ReviewPromptFactory(),
                    new ReviewJsonParser(), mock, apiKey,
                    System.getenv().getOrDefault("AI_MODEL", "claude-opus-4-8"), 8000);
            scores.add(score("claude", claude::review));
        }

        writeReport(scores);
        printReport(scores);

        // Regression floors — deterministic engine only.
        assertThat(heuristic.heuristicTp())
                .as("heuristic engine must catch >=80%% of heuristic-detectable bugs")
                .isGreaterThanOrEqualTo((int) Math.ceil(heuristic.heuristicTotal() * 0.8));
        assertThat(heuristic.cleanFalsePositives())
                .as("heuristic engine must not flag clean diffs")
                .isZero();
        assertThat(heuristic.precision())
                .as("heuristic engine precision floor")
                .isGreaterThanOrEqualTo(0.8);
    }

    private EngineScore score(String engineName, BiFunction<String, String, AiReviewResult> engine) {
        FindingAnchorValidator validator = new FindingAnchorValidator();
        int tp = 0, fn = 0, fp = 0;
        int heuristicTp = 0, heuristicTotal = 0, semanticTp = 0, semanticTotal = 0;
        int cleanFp = 0, demoted = 0;
        Map<IssueCategory, int[]> perCategory = new EnumMap<>(IssueCategory.class);

        for (EvalCase c : cases) {
            AiReviewResult raw = engine.apply(c.title(), c.diff());
            var validated = validator.validate(c.diff(), raw.findings());
            demoted += validated.demoted();

            List<AiReviewResult.Finding> findings = validated.findings().stream()
                    .filter(f -> f.category() != IssueCategory.TEST_COVERAGE)
                    .toList();

            Set<AiReviewResult.Finding> matched = new HashSet<>();
            for (ExpectedBug bug : c.expected()) {
                boolean heuristicBug = "HEURISTIC".equals(c.detectableBy());
                if (heuristicBug) {
                    heuristicTotal++;
                } else {
                    semanticTotal++;
                }
                AiReviewResult.Finding hit = findings.stream()
                        .filter(f -> !matched.contains(f) && matches(bug, f))
                        .findFirst().orElse(null);
                int[] cat = perCategory.computeIfAbsent(primaryCategory(bug), k -> new int[3]);
                if (hit != null) {
                    matched.add(hit);
                    tp++;
                    cat[0]++;
                    if (heuristicBug) {
                        heuristicTp++;
                    } else {
                        semanticTp++;
                    }
                } else {
                    fn++;
                    cat[1]++;
                }
            }
            int unexpected = findings.size() - matched.size();
            fp += unexpected;
            if (c.expected().isEmpty()) {
                cleanFp += unexpected;
            }
            for (AiReviewResult.Finding f : findings) {
                if (!matched.contains(f)) {
                    perCategory.computeIfAbsent(f.category(), k -> new int[3])[2]++;
                }
            }
        }
        return new EngineScore(engineName, tp, fn, fp, heuristicTp, heuristicTotal,
                semanticTp, semanticTotal, cleanFp, demoted, perCategory);
    }

    private boolean matches(ExpectedBug bug, AiReviewResult.Finding finding) {
        String expectedPath = normalize(bug.filePath());
        String actualPath = normalize(finding.filePath());
        if (!expectedPath.equals(actualPath) && !actualPath.endsWith("/" + expectedPath)
                && !expectedPath.endsWith("/" + actualPath)) {
            return false;
        }
        if (!bug.categories().contains(finding.category().name())) {
            return false;
        }
        if (bug.lineNumber() == null || finding.lineNumber() == null) {
            return true; // file-level match
        }
        return Math.abs(bug.lineNumber() - finding.lineNumber()) <= LINE_TOLERANCE;
    }

    private IssueCategory primaryCategory(ExpectedBug bug) {
        return bug.categories().stream().findFirst()
                .map(name -> IssueCategory.valueOf(name))
                .orElse(IssueCategory.CODE_QUALITY);
    }

    private static String normalize(String path) {
        String p = path.trim().replace('\\', '/');
        if (p.startsWith("a/") || p.startsWith("b/")) {
            p = p.substring(2);
        }
        return p;
    }

    private void writeReport(List<EngineScore> scores) throws Exception {
        long labeledBugs = cases.stream().mapToLong(c -> c.expected().size()).sum();
        long cleanCases = cases.stream().filter(c -> c.expected().isEmpty()).count();

        ObjectNode root = MAPPER.createObjectNode();
        root.put("generatedAt", Instant.now().toString());
        ObjectNode dataset = root.putObject("dataset");
        dataset.put("cases", cases.size());
        dataset.put("buggyCases", cases.size() - (int) cleanCases);
        dataset.put("cleanCases", (int) cleanCases);
        dataset.put("labeledBugs", labeledBugs);
        root.put("methodology",
                "Each case is a unified diff with hand-labeled planted bugs (file, line, accepted "
                + "categories). A finding is a true positive when file matches, category is accepted, "
                + "and the line is within " + LINE_TOLERANCE + " lines of the label. Unexpected "
                + "findings count as false positives (a strict lower bound on precision). "
                + "TEST_COVERAGE findings are excluded from scoring. Anchors are validated against "
                + "the diff; demoted anchors measure line-number hallucination.");

        ArrayNode engines = root.putArray("engines");
        for (EngineScore s : scores) {
            ObjectNode e = engines.addObject();
            e.put("engine", s.engine());
            e.put("truePositives", s.tp());
            e.put("falseNegatives", s.fn());
            e.put("falsePositives", s.fp());
            e.put("precision", round(s.precision()));
            e.put("recall", round(s.recall()));
            e.put("f1", round(s.f1()));
            e.put("heuristicDetectableRecall",
                    round(s.heuristicTotal() == 0 ? 1 : (double) s.heuristicTp() / s.heuristicTotal()));
            e.put("semanticRecall",
                    round(s.semanticTotal() == 0 ? 1 : (double) s.semanticTp() / s.semanticTotal()));
            e.put("cleanFalsePositives", s.cleanFalsePositives());
            e.put("demotedAnchors", s.demotedAnchors());
            ArrayNode categories = e.putArray("categories");
            s.perCategory().forEach((category, counts) -> {
                ObjectNode c = categories.addObject();
                c.put("category", category.name());
                c.put("tp", counts[0]);
                c.put("fn", counts[1]);
                c.put("fp", counts[2]);
            });
        }

        Path out = Path.of("target", "evals", "report.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private void printReport(List<EngineScore> scores) {
        System.out.println("==================== EVAL REPORT ====================");
        for (EngineScore s : scores) {
            System.out.printf(Locale.ROOT,
                    "%-10s precision=%.2f recall=%.2f f1=%.2f | heuristic-bugs %d/%d, semantic-bugs %d/%d | clean FPs=%d, demoted anchors=%d%n",
                    s.engine(), s.precision(), s.recall(), s.f1(),
                    s.heuristicTp(), s.heuristicTotal(), s.semanticTp(), s.semanticTotal(),
                    s.cleanFalsePositives(), s.demotedAnchors());
        }
        System.out.println("=====================================================");
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
