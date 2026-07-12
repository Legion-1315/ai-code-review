package com.codereview.service.context;

import java.util.List;

/**
 * Repository context gathered for a review: the full contents of the files touched by
 * the diff, plus a bounded set of same-repo files they import. Gives the model the code
 * *around* the change instead of a keyhole view of the hunks.
 */
public record RepoContext(String repoFullName, String ref, List<ContextFile> files,
                          boolean truncated) {

    public record ContextFile(String path, String content, boolean imported) {
    }

    public static RepoContext empty() {
        return new RepoContext(null, null, List.of(), false);
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public int totalChars() {
        return files.stream().mapToInt(f -> f.content().length()).sum();
    }
}
