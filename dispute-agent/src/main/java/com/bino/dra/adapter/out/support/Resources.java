package com.bino.dra.adapter.out.support;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class Resources {

    private Resources() {
    }

    public static String text(Resource resource, String description) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Unreadable " + description + ": " + resource.getFilename(), e);
        }
    }
}
