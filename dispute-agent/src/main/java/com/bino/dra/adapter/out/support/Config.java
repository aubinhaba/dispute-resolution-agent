package com.bino.dra.adapter.out.support;

public final class Config {

    private Config() {
    }

    public static int requireAtLeastOne(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be >= 1, got: " + value);
        }
        return value;
    }
}
