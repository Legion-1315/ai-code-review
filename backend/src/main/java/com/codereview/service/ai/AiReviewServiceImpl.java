package com.codereview.service.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.codereview.service.context.RepoContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Primary review engine. Uses the Claude API when {@code ANTHROPIC_API_KEY} is configured,
 * and transparently falls back to the deterministic {@link MockReviewEngine} when the key is
 * absent or a live call fails — so the application always returns a usable review.
 *
 * <p>Prompt-caching layout (prefix match: stable first, volatile last):
 * system prompt (cache breakpoint) → repo-context block (cache breakpoint) → diff.
 * Repeat reviews of the same PR state — retries, rapid synchronize events — read the
 * large context block from cache at ~10% of input price instead of re-paying it.
 */
@Service
public class AiReviewServiceImpl implements AiReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiReviewServiceImpl.class);

    private final ReviewPromptFactory promptFactory;
    private final ReviewJsonParser jsonParser;
    private final MockReviewEngine mockEngine;
    private final String apiKey;
    private final String model;
    private final long maxTokens;

    /** Lazily-initialized; null when no API key is configured. */
    private volatile AnthropicClient client;

    public AiReviewServiceImpl(ReviewPromptFactory promptFactory,
                               ReviewJsonParser jsonParser,
                               MockReviewEngine mockEngine,
                               @Value("${ai.api-key:}") String apiKey,
                               @Value("${ai.model}") String model,
                               @Value("${ai.max-tokens}") long maxTokens) {
        this.promptFactory = promptFactory;
        this.jsonParser = jsonParser;
        this.mockEngine = mockEngine;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public AiReviewResult review(String prTitle, String diff, RepoContext context) {
        if (!StringUtils.hasText(apiKey)) {
            log.debug("No ANTHROPIC_API_KEY set — using heuristic mock reviewer.");
            return mockEngine.review(prTitle, diff);
        }
        try {
            return callClaude(prTitle, diff, context);
        } catch (Exception e) {
            log.warn("Claude review failed ({}). Falling back to heuristic reviewer.", e.getMessage());
            return mockEngine.review(prTitle, diff);
        }
    }

    private AiReviewResult callClaude(String prTitle, String diff, RepoContext context) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.HIGH)
                        .build())
                // Stable across every review — always a cache breakpoint.
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(promptFactory.systemPrompt())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()));

        if (context != null && !context.isEmpty()) {
            // Repo context is the large, per-PR-state stable prefix; the diff prompt is
            // the volatile suffix and stays after the last breakpoint.
            List<ContentBlockParam> blocks = new ArrayList<>(2);
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder()
                    .text(promptFactory.contextText(context))
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build()));
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder()
                    .text(promptFactory.userPrompt(prTitle, diff))
                    .build()));
            builder.addUserMessageOfBlockParams(blocks);
        } else {
            builder.addUserMessage(promptFactory.userPrompt(prTitle, diff));
        }

        Message response = client().messages().create(builder.build());
        log.info("Claude review usage: {}", response.usage());

        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
        }
        return jsonParser.parse(text.toString(), true);
    }

    private AnthropicClient client() {
        AnthropicClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                    client = local;
                }
            }
        }
        return local;
    }
}
