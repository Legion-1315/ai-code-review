package com.codereview.service.ai;

import com.codereview.domain.IssueCategory;
import com.codereview.domain.Severity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FindingAnchorValidatorTest {

    private final FindingAnchorValidator validator = new FindingAnchorValidator();

    private static final String DIFF = """
            diff --git a/src/main/java/com/example/UserDao.java b/src/main/java/com/example/UserDao.java
            --- a/src/main/java/com/example/UserDao.java
            +++ b/src/main/java/com/example/UserDao.java
            @@ -10,6 +10,8 @@ public class UserDao {
                 private final DataSource dataSource;

                 public User findByName(String name) {
            +        String sql = "SELECT * FROM users WHERE name = '" + name + "'";
            +        return jdbc.queryForObject(sql, userMapper);
                 }
             }
            diff --git a/src/main/java/com/example/Util.java b/src/main/java/com/example/Util.java
            --- a/src/main/java/com/example/Util.java
            +++ b/src/main/java/com/example/Util.java
            @@ -1,4 +1,5 @@
             public class Util {
            +    public static final String API_KEY = "sk-secret";
             }
            """;

    private static AiReviewResult.Finding finding(String file, Integer line) {
        return new AiReviewResult.Finding(file, line, IssueCategory.SECURITY, Severity.HIGH,
                "msg", "fix");
    }

    @Test
    @DisplayName("finding on a real added line is kept as-is")
    void anchoredKept() {
        var result = validator.validate(DIFF, List.of(
                finding("src/main/java/com/example/UserDao.java", 13)));
        assertThat(result.findings().get(0).lineNumber()).isEqualTo(13);
        assertThat(result.snapped()).isZero();
        assertThat(result.demoted()).isZero();
    }

    @Test
    @DisplayName("context lines are valid anchors too")
    void contextLineAnchor() {
        var result = validator.validate(DIFF, List.of(
                finding("src/main/java/com/example/UserDao.java", 12)));
        assertThat(result.findings().get(0).lineNumber()).isEqualTo(12);
        assertThat(result.demoted()).isZero();
    }

    @Test
    @DisplayName("off-by-two hallucination is snapped to the nearest real line")
    void nearMissSnapped() {
        // Diff covers new-file lines 10..17 for UserDao; 19 is 2 away from 17
        var result = validator.validate(DIFF, List.of(
                finding("src/main/java/com/example/UserDao.java", 19)));
        assertThat(result.snapped()).isEqualTo(1);
        assertThat(result.findings().get(0).lineNumber()).isNotNull();
        assertThat(result.demoted()).isZero();
    }

    @Test
    @DisplayName("line far outside any hunk is demoted to file level")
    void farLineDemoted() {
        var result = validator.validate(DIFF, List.of(
                finding("src/main/java/com/example/UserDao.java", 400)));
        assertThat(result.findings().get(0).lineNumber()).isNull();
        assertThat(result.demoted()).isEqualTo(1);
    }

    @Test
    @DisplayName("file not present in the diff loses its line anchor")
    void unknownFileDemoted() {
        var result = validator.validate(DIFF, List.of(
                finding("src/main/java/com/example/DoesNotExist.java", 5)));
        assertThat(result.findings().get(0).lineNumber()).isNull();
        assertThat(result.demoted()).isEqualTo(1);
    }

    @Test
    @DisplayName("model path variants (b/ prefix, basename) resolve to the diff file")
    void pathNormalization() {
        var withPrefix = validator.validate(DIFF, List.of(
                finding("b/src/main/java/com/example/UserDao.java", 13)));
        assertThat(withPrefix.findings().get(0).lineNumber()).isEqualTo(13);
        assertThat(withPrefix.demoted()).isZero();

        var basename = validator.validate(DIFF, List.of(
                finding("com/example/UserDao.java", 13)));
        assertThat(basename.demoted()).isZero();
    }

    @Test
    @DisplayName("file-level findings (null line) pass through untouched")
    void nullLinePasses() {
        var result = validator.validate(DIFF, List.of(
                finding("src/main/java/com/example/UserDao.java", null)));
        assertThat(result.findings().get(0).lineNumber()).isNull();
        assertThat(result.snapped()).isZero();
        assertThat(result.demoted()).isZero();
    }

    @Test
    @DisplayName("mock engine's own findings all validate cleanly")
    void mockEngineFindingsAnchor() {
        MockReviewEngine mock = new MockReviewEngine();
        AiReviewResult mockResult = mock.review("test", DIFF);
        assertThat(mockResult.findings()).isNotEmpty();

        var result = validator.validate(DIFF, mockResult.findings());
        assertThat(result.demoted()).isZero();
        assertThat(result.snapped()).isZero();
    }
}
