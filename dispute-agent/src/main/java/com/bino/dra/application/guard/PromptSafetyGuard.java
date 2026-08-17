package com.bino.dra.application.guard;

import com.bino.dra.domain.model.Dispute;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptSafetyGuard {

    // Non-digit lookarounds: without them a 25-digit run offers 19-digit substrings to test
    private static final Pattern DIGIT_RUN =
            Pattern.compile("(?<![0-9])[0-9](?:[ -]?[0-9]){12,18}(?![0-9])");

    private static final Pattern SEPARATORS = Pattern.compile("[ -]");
    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n]+");
    private static final Pattern HASHES = Pattern.compile("#{2,}");

    // Returns the field NAME, never its content: this reason travels into the audit trail
    public Optional<String> reject(Dispute dispute) {
        Objects.requireNonNull(dispute, "dispute required");
        if (containsPan(dispute.issuerClaim())) {
            return Optional.of("issuer claim");
        }
        if (containsPan(dispute.transactionId())) {
            return Optional.of("transaction identifier");
        }
        if (containsPan(dispute.merchantId())) {
            return Optional.of("merchant identifier");
        }
        if (containsPan(dispute.disputeId())) {
            return Optional.of("dispute identifier");
        }
        return Optional.empty();
    }

    public Dispute neutralise(Dispute dispute) {
        Objects.requireNonNull(dispute, "dispute required");
        String claim = dispute.issuerClaim();
        if (claim == null) {
            return dispute;
        }
        String safe = neutraliseDelimiters(claim);
        if (safe.equals(claim)) {
            return dispute;
        }
        return new Dispute(
                dispute.disputeId(),
                dispute.transactionId(),
                dispute.merchantId(),
                dispute.network(),
                dispute.reasonCode(),
                dispute.disputedAmount(),
                dispute.raisedAt(),
                dispute.representmentDueBy(),
                safe);
    }

    static String neutraliseDelimiters(String text) {
        // Quotes and line breaks both let the claim escape the data block it is wrapped in
        String out = text
                .replace('"', '\'')
                .replace('`', '\'')
                .replace('<', '(')
                .replace('>', ')')
                .replace('[', '(')
                .replace(']', ')');
        out = LINE_BREAKS.matcher(out).replaceAll(" ");
        return HASHES.matcher(out).replaceAll("#");
    }

    // Regex THEN Luhn: the regex alone would reject 16-digit order numbers
    static boolean containsPan(String text) {
        if (text == null) {
            return false;
        }
        Matcher candidates = DIGIT_RUN.matcher(text);
        while (candidates.find()) {
            if (luhnValid(SEPARATORS.matcher(candidates.group()).replaceAll(""))) {
                return true;
            }
        }
        return false;
    }

    static boolean luhnValid(String digits) {
        int sum = 0;
        boolean doubling = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int value = digits.charAt(i) - '0';
            if (doubling) {
                value *= 2;
                if (value > 9) {
                    value -= 9;
                }
            }
            sum += value;
            doubling = !doubling;
        }
        return sum % 10 == 0;
    }
}
