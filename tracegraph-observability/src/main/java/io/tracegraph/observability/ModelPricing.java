package io.tracegraph.observability;

public record ModelPricing(double inputPer1kTokens, double outputPer1kTokens) {
    public double cost(int promptTokens, int completionTokens) {
        return (promptTokens / 1000.0) * inputPer1kTokens
             + (completionTokens / 1000.0) * outputPer1kTokens;
    }

    public static ModelPricing gpt4o() { return new ModelPricing(0.0025, 0.010); }
    public static ModelPricing gpt4oMini() { return new ModelPricing(0.000150, 0.000600); }
    public static ModelPricing claude3Haiku() { return new ModelPricing(0.00025, 0.00125); }
    public static ModelPricing claude3Sonnet() { return new ModelPricing(0.003, 0.015); }
    public static ModelPricing flat(double perInputToken, double perOutputToken) {
        return new ModelPricing(perInputToken * 1000, perOutputToken * 1000);
    }
}
