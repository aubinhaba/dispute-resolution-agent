package com.bino.dra.adapter.out.support;

import java.util.regex.Pattern;

public final class Text {

    private static final Pattern REPEATED_SPACES = Pattern.compile(" +");
    private static final String ELLIPSIS = "...";

    private Text() {
    }

    public static String flatten(String text) {
        if (text == null) {
            return "";
        }
        return REPEATED_SPACES.matcher(text.replace('\n', ' ')).replaceAll(" ").strip();
    }

    public static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + ELLIPSIS;
    }
}
