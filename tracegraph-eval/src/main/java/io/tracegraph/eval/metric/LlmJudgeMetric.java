package io.tracegraph.eval.metric;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;

import java.util.List;
import java.util.Objects;

public final class LlmJudgeMetric<S> implements Metric<S> {

    private static final String DEFAULT_PROMPT =
            "You are an expert evaluator. Given the question, expected answer, and actual output, "
            + "rate the quality of the actual output as a float between 0.0 (completely wrong) and "
            + "1.0 (perfect). Respond with only the score on the first line, then optionally a "
            + "one-sentence explanation.\n\n"
            + "Question: {input}\nExpected: {expected}\nActual: {actual}";

    private final LlmClient judge;
    private final String model;
    private final String promptTemplate;
    private final double passThreshold;

    private LlmJudgeMetric(Builder<S> b) {
        this.judge = b.judge;
        this.model = b.model;
        this.promptTemplate = b.promptTemplate;
        this.passThreshold = b.passThreshold;
    }

    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    public double passThreshold() {
        return passThreshold;
    }

    @Override
    public String name() {
        return "llm-judge";
    }

    @Override
    public boolean canScore(EvalCase<S> evalCase) {
        return evalCase != null && evalCase.expected() != null;
    }

    @Override
    public MetricScore score(EvalCase<S> evalCase, S actual, long latencyMs) {
        String prompt = promptTemplate
                .replace("{input}", String.valueOf(evalCase.input()))
                .replace("{expected}", String.valueOf(evalCase.expected()))
                .replace("{actual}", String.valueOf(actual));

        LlmRequest request = LlmRequest.builder()
                .model(model)
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.0)
                .maxTokens(256)
                .build();

        LlmResponse response = judge.complete(request);
        String text = response.content().strip();
        String[] lines = text.split("\n", 2);

        double parsedScore;
        try {
            parsedScore = Double.parseDouble(lines[0].strip());
            parsedScore = Math.max(0.0, Math.min(1.0, parsedScore));
        } catch (NumberFormatException e) {
            return MetricScore.fail("llm-judge", 0.0,
                    "Judge returned non-numeric score: " + lines[0]);
        }

        String detail = lines.length > 1 ? lines[1].strip() : null;
        if (parsedScore >= passThreshold) {
            return MetricScore.pass("llm-judge", parsedScore);
        }
        return MetricScore.fail("llm-judge", parsedScore,
                detail != null ? detail : "Score " + parsedScore + " below threshold " + passThreshold);
    }

    public static final class Builder<S> {
        private LlmClient judge;
        private String model;
        private String promptTemplate = DEFAULT_PROMPT;
        private double passThreshold = 0.7;

        private Builder() {}

        public Builder<S> judge(LlmClient judge) {
            this.judge = Objects.requireNonNull(judge, "judge");
            return this;
        }

        public Builder<S> model(String model) {
            this.model = Objects.requireNonNull(model, "model");
            return this;
        }

        public Builder<S> promptTemplate(String template) {
            this.promptTemplate = Objects.requireNonNull(template, "promptTemplate");
            return this;
        }

        public Builder<S> passThreshold(double threshold) {
            if (threshold < 0 || threshold > 1) {
                throw new IllegalArgumentException("passThreshold must be in [0, 1]");
            }
            this.passThreshold = threshold;
            return this;
        }

        public LlmJudgeMetric<S> build() {
            Objects.requireNonNull(judge, "judge is required");
            Objects.requireNonNull(model, "model is required");
            return new LlmJudgeMetric<>(this);
        }
    }
}
