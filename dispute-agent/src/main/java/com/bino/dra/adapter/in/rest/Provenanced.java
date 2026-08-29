package com.bino.dra.adapter.in.rest;

public record Provenanced<T>(T value, Provenance provenance) {

    static <T> Provenanced<T> attested(T value) {
        return new Provenanced<>(value, Provenance.ATTESTED);
    }

    static <T> Provenanced<T> model(T value) {
        return new Provenanced<>(value, Provenance.MODEL);
    }

    static <T> Provenanced<T> untrusted(T value) {
        return new Provenanced<>(value, Provenance.UNTRUSTED);
    }
}
