package com.rpo.mimico.utils;

import java.text.Normalizer;

public class WordNormalizer {

    private WordNormalizer() {}

    public static String normalize(String word) {
        if (word == null) return "";
        String normalized = Normalizer.normalize(word, Normalizer.Form.NFC);
        normalized = normalized.replaceAll("\\p{M}", "");
        normalized = normalized.toLowerCase();
        normalized = normalized.trim().replaceAll("\\s+", " ");

        return normalized;
    }

    public static boolean matches(String word1, String word2) {
        return normalize(word1).equals(normalize(word2));
    }
}
