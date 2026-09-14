package com.bino.dra.adapter.out.llm;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

// One instance per call, like ToolCallRecorder: a shared counter would merge concurrent disputes
public final class TokenBudgetAdvisor implements CallAdvisor {

    private final long maxTokens;
    private long consumed;

    public TokenBudgetAdvisor(long maxTokens) {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("token budget must be >= 1, got: " + maxTokens);
        }
        this.maxTokens = maxTokens;
    }

    @Override
    public String getName() {
        return "dra-token-budget";
    }

    // After the tool-calling loop, so every round of it passes through here; before it, only one would
    @Override
    public int getOrder() {
        return ToolCallingAdvisor.DEFAULT_ORDER + 10;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // Checked before the call, not after: it is the next call that must not happen
        if (consumed >= maxTokens) {
            throw new TokenBudgetExceededException(consumed, maxTokens);
        }
        ChatClientResponse response = chain.nextCall(request);
        consumed += tokensOf(response);
        return response;
    }

    public long consumed() {
        return consumed;
    }

    public long maxTokens() {
        return maxTokens;
    }

    static long tokensOf(ChatClientResponse response) {
        ChatResponse chatResponse = response == null ? null : response.chatResponse();
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return 0;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        return usage == null || usage.getTotalTokens() == null ? 0 : usage.getTotalTokens();
    }

    public static final class TokenBudgetExceededException extends RuntimeException {

        private final long consumed;
        private final long maxTokens;

        public TokenBudgetExceededException(long consumed, long maxTokens) {
            super("token budget reached: " + consumed + " consumed of " + maxTokens);
            this.consumed = consumed;
            this.maxTokens = maxTokens;
        }

        public long consumed() {
            return consumed;
        }

        public long maxTokens() {
            return maxTokens;
        }
    }
}
