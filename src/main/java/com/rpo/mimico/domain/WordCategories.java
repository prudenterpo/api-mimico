package com.rpo.mimico.domain;

import java.util.List;

public final class WordCategories {

    public static final List<String> DATABASE_NAMES = List.of("eu_sou", "eu_faco", "objeto");

    private WordCategories() {
    }

    public static String contractName(String databaseName) {
        return switch (databaseName) {
            case "eu_sou" -> "EU_SOU";
            case "eu_faco" -> "EU_FACO";
            case "objeto" -> "OBJETO";
            default -> databaseName;
        };
    }
}
