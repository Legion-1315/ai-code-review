package com.codereview.service.context;

import com.codereview.service.ai.ReviewPromptFactory;
import com.codereview.service.github.GitHubContentFetcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RepoContextServiceTest {

    private static final String DIFF = """
            --- a/src/main/java/com/shop/OrderService.java
            +++ b/src/main/java/com/shop/OrderService.java
            @@ -1,3 +1,4 @@
             public class OrderService {
            +    private final PriceCalculator calculator;
             }
            --- a/README.md
            +++ b/README.md
            @@ -1,1 +1,2 @@
             # Shop
            +Now with orders.
            """;

    private static final String ORDER_SERVICE_SOURCE = """
            package com.shop;

            import com.shop.pricing.PriceCalculator;

            public class OrderService {
                private final PriceCalculator calculator;
            }
            """;

    private static final String TREE_JSON = """
            {"tree": [
              {"path": "src/main/java/com/shop/OrderService.java", "type": "blob"},
              {"path": "src/main/java/com/shop/pricing/PriceCalculator.java", "type": "blob"},
              {"path": "README.md", "type": "blob"}
            ]}
            """;

    /** URL-substring → response body; unmatched URLs return empty. */
    private static GitHubContentFetcher fake(Map<String, String> bySubstring) {
        return url -> bySubstring.entrySet().stream()
                .filter(e -> url.contains(e.getKey()))
                .map(e -> e.getValue())
                .findFirst()
                .map(Optional::of)
                .orElse(Optional.empty());
    }

    private static RepoContextService service(GitHubContentFetcher github) {
        return new RepoContextService(github, true, 48_000, 12_000, 4);
    }

    @Test
    @DisplayName("touched files are extracted from the diff, skipping deletions and binaries")
    void touchedFiles() {
        RepoContextService service = service(url -> Optional.empty());
        String diff = DIFF + "\n+++ /dev/null\n+++ b/logo.png\n+++ b/package-lock.json\n";
        assertThat(service.touchedFiles(diff)).containsExactly(
                "src/main/java/com/shop/OrderService.java", "README.md");
    }

    @Test
    @DisplayName("fetches touched files and resolves one level of Java imports via the tree")
    void fetchWithImports() {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("git/trees", TREE_JSON);
        responses.put("/OrderService.java", ORDER_SERVICE_SOURCE);
        responses.put("/README.md", "# Shop");
        responses.put("/PriceCalculator.java", "public class PriceCalculator {}");

        RepoContext context = service(fake(responses)).fetch("owner/repo", "abc123", DIFF);

        assertThat(context.files()).extracting(RepoContext.ContextFile::path).containsExactly(
                "src/main/java/com/shop/OrderService.java",
                "README.md",
                "src/main/java/com/shop/pricing/PriceCalculator.java");
        assertThat(context.files().get(2).imported()).isTrue();
        assertThat(context.ref()).isEqualTo("abc123");
        assertThat(context.truncated()).isFalse();
    }

    @Test
    @DisplayName("per-file and total budgets are enforced and flagged as truncated")
    void budgets() {
        GitHubContentFetcher bigFiles = url -> Optional.of("x".repeat(50_000));
        RepoContextService small = new RepoContextService(bigFiles, true, 1000, 800, 0);

        RepoContext context = small.fetch("owner/repo", "abc", DIFF);

        assertThat(context.truncated()).isTrue();
        assertThat(context.totalChars()).isLessThanOrEqualTo(1000 + 40); // + truncation markers
    }

    @Test
    @DisplayName("network failures degrade to an empty context, never an exception")
    void failuresAreGraceful() {
        RepoContext context = service(url -> Optional.empty()).fetch("owner/repo", "abc", DIFF);
        assertThat(context.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("disabled flag and malformed repo names short-circuit")
    void guards() {
        GitHubContentFetcher github = url -> Optional.of("should never be used");
        RepoContextService disabled = new RepoContextService(github, false, 48_000, 12_000, 4);
        assertThat(disabled.fetch("owner/repo", "abc", DIFF).isEmpty()).isTrue();
        assertThat(service(github).fetch("not-a-repo", "abc", DIFF).isEmpty()).isTrue();
        assertThat(service(github).fetch(null, "abc", DIFF).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("context block renders file tags the model can navigate")
    void promptRendering() {
        RepoContext context = new RepoContext("owner/repo", "abc123", List.of(
                new RepoContext.ContextFile("src/A.java", "class A {}", false),
                new RepoContext.ContextFile("src/B.java", "class B {}", true)), false);

        String text = new ReviewPromptFactory().contextText(context);

        assertThat(text).contains("owner/repo@abc123");
        assertThat(text).contains("<file path=\"src/A.java\">");
        assertThat(text).contains("<file path=\"src/B.java\" reason=\"imported by a touched file\">");
        assertThat(text).contains("class B {}");
    }
}
