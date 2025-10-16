package com.rpo.mimico.utils;

import java.text.Normalizer;

public class WordNormalizer {

    private WordNormalizer() {}

    public static String normalize(String word) {
        if (word == null) return "";

        String normalized = Normalizer.normalize(word, Normalizer.Form.NFD);

        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return normalized.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    public static boolean matches(String word1, String word2) {
        return normalize(word1).equals(normalize(word2));
    }
}
